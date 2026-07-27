package com.nova.cfquota.data.repository

import com.nova.cfquota.core.Constants
import com.nova.cfquota.core.ErrorType
import com.nova.cfquota.core.Resource
import com.nova.cfquota.data.local.SettingsDataStore
import com.nova.cfquota.data.remote.ApiResult
import com.nova.cfquota.data.remote.CloudflareApi
import com.nova.cfquota.domain.model.CfSettings
import com.nova.cfquota.domain.model.RefreshPrefs
import com.nova.cfquota.domain.model.UsageData
import com.nova.cfquota.domain.repository.CloudflareRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class CloudflareRepositoryImpl(
    private val api: CloudflareApi,
    private val dataStore: SettingsDataStore
) : CloudflareRepository {

    override val settings: Flow<CfSettings> = dataStore.settingsFlow

    override val refreshPrefs: Flow<RefreshPrefs> = dataStore.refreshPrefsFlow

    override suspend fun saveSettings(settings: CfSettings) = dataStore.save(settings)

    override suspend fun clearSettings() = dataStore.clear()

    override suspend fun setAutoRefresh(enabled: Boolean) = dataStore.setAutoRefresh(enabled)

    override suspend fun setRefreshInterval(minutes: Int) = dataStore.setRefreshInterval(minutes)

    override suspend fun getTodayUsage(settings: CfSettings): Resource<UsageData> =
        fetch(settings)

    override suspend fun testCredentials(settings: CfSettings): Resource<UsageData> =
        fetch(settings)

    /**
     * Single fetch with one transparent retry on transient failures.
     *
     * v1.6.1 (and earlier) had a silent UX bug: when Cloudflare's GraphQL
     * edge returned a 200 with a body that failed to parse — or returned
     * a structured body with `viewer.accounts = []` due to a brief auth
     * propagation hiccup — the message "未找到账户数据，请检查 Account ID 是否正确"
     * was painted on the widget. The hint pointed at the wrong root cause
     * (Account ID was almost always fine) and there was no recovery short
     * of tapping the refresh button.
     *
     * To eliminate both misclassification and the "stuck-error for 15–120
     * minutes" UX:
     *  1. Differentiate error messages by *actual* root cause.
     *  2. Silently retry EXACTLY once when the failure looks transient
     *     (network IO exception, unparseable body, or empty `accounts`).
     *     Identified 401/403/429 errors are NOT retried — those are
     *     deterministic and need user action.
     *  3. Aggregate transientFailure from [ApiResult.transientFailure] so a
     *     parse-failed 200 is treated identically to a thrown IOException.
     */
    private suspend fun fetch(settings: CfSettings): Resource<UsageData> =
        withContext(Dispatchers.IO) {
            if (!settings.isConfigured) {
                return@withContext Resource.Error(
                    ErrorType.UNAUTHORIZED,
                    "尚未配置 Account ID 或 API Token"
                )
            }
            // Cloudflare analytics use UTC. Mirror edgetunnel's known-working
            // window: datetime_geq = start of UTC day, datetime_leq = now.
            val nowInstant = java.time.Instant.now()
            val startOfUtcDay = nowInstant
                .atZone(java.time.ZoneOffset.UTC)
                .toLocalDate()
                .atStartOfDay(java.time.ZoneOffset.UTC)
                .toInstant()
            val dtGeq = startOfUtcDay.toString()   // e.g. 2026-07-26T00:00:00Z
            val dtLeq = nowInstant.toString()       // e.g. 2026-07-26T19:13:04Z

            // First attempt
            var result = runQuery(settings, dtGeq, dtLeq)
            var outcome = classify(settings, result, attempt = 1)

            // Cheap, one-shot retry only when this looks like a transient
            // edge blip — never for credential issues (401/403) or explicit
            // rate limiting (429). 350 ms is short enough not to be felt
            // and long enough to clear the most common HTTP/2 stream reset
            // window. Waited before re-querying; the 350 ms only happens
            // when a retry is needed, so happy-path latency is unchanged.
            if (outcome is Outcome.Retryable) {
                delay(350)
                result = runQuery(settings, dtGeq, dtLeq)
                outcome = classify(settings, result, attempt = 2)
            }

            outcome.toResource()
        }

    /** Wraps [CloudflareApi.query] so we can re-run it during the silent retry. */
    private suspend fun runQuery(
        settings: CfSettings,
        dtGeq: String,
        dtLeq: String
    ): ApiResult = api.query(
        accountTag = settings.accountId,
        apiToken = settings.apiToken,
        dtGeq = dtGeq,
        dtLeq = dtLeq
    )

    /**
     * Maps a raw [ApiResult] from a single attempt onto an internal
     * [Outcome] that captures whether another attempt is worth trying.
     *
     * Distinguishing rules:
     *  - 401 / 403: deterministic credential issue — no retry.
     *  - 429: deterministic rate limit — no retry (would just lengthen
     *    the cooldown).
     *  - GraphQL `errors[]` populated: server is talking back — read
     *    the message once, no retry (the message itself is meaningful).
     *  - `body.data.viewer.accounts == []` and after the retry still
     *    empty: rename the misleading "Account ID 错误" hint to a
     *    accurate "Token 权限或 Account ID 可能不匹配" hint.
     *  - Network exception / bodyParseFailed: transient — retry once.
     */
    private fun classify(settings: CfSettings, result: ApiResult, attempt: Int): Outcome {
        // 1) Auth / rate-limit short-circuits come back directly from the API.
        // No classification needed; these are terminal.
        when (result.statusCode) {
            401, 403 -> return Outcome.Terminal(
                Resource.Error(
                    ErrorType.UNAUTHORIZED,
                    "API Token 无效或权限不足 (${result.statusCode})"
                )
            )
            429 -> return Outcome.Terminal(
                Resource.Error(
                    ErrorType.RATE_LIMITED,
                    "请求过于频繁，已触发接口限流 (429)"
                )
            )
        }

        val body = result.body

        // 2) Server returned GraphQL `errors[]` — that is the authoritative
        // problem report and should be surfaced once, no retry.
        if (body?.errors?.isNotEmpty() == true) {
            val msg = body.errors.firstOrNull()?.message ?: "GraphQL 查询失败"
            val type = if (msg.contains("authentication", true) ||
                msg.contains("Unauthorized", true)
            ) ErrorType.UNAUTHORIZED else ErrorType.GRAPHQL
            return Outcome.Terminal(Resource.Error(type, msg))
        }

        // 3) Network-layer failure or unparseable body — retry.
        if (result.transientFailure) {
            return if (attempt < 2) Outcome.Retryable
            else Outcome.Terminal(
                Resource.Error(
                    ErrorType.NETWORK,
                    "网络连接异常，请稍后重试"
                )
            )
        }

        val account = body?.data?.viewer?.accounts?.firstOrNull()

        // 4) Real "no account" branch — could still be a transient Token
        // propagation delay on CF's side, so retry once. After the retry,
        // give a precise message instead of the old "Account ID 错误" hint.
        if (account == null) {
            return if (attempt < 2) Outcome.Retryable
            else Outcome.Terminal(
                Resource.Error(
                    ErrorType.GRAPHQL,
                    "API Token 可能无权访问该 Account ID，请检查 Token 权限或 Account ID 是否正确"
                )
            )
        }

        // 5) Success path — sum metrics.
        val workers = account.workersGroups.sumOf { it.sum?.requests ?: 0L }
        val pages = account.pagesGroups.sumOf { it.sum?.requests ?: 0L }
        return Outcome.Terminal(
            Resource.Success(
                UsageData(
                    workersRequests = workers,
                    pagesRequests = pages,
                    dailyQuota = if (settings.dailyQuota > 0) settings.dailyQuota
                    else Constants.DEFAULT_DAILY_QUOTA
                )
            )
        )
    }

    /**
     * Internal sum-type returned by [classify]. Keeps the retry / no-retry
     * decision out of the resource semantics so the wrapping [fetch] loop
     * is a one-liner. Every [Outcome.toResource] implementation must yield
     * a concrete [Resource] — for [Retryable] this is unreachable in normal
     * flow (the loop retries instead), but the contract is total for safety.
     */
    private sealed interface Outcome {
        data class Terminal(val resource: Resource<UsageData>) : Outcome {
            override fun toResource(): Resource<UsageData> = resource
        }
        data object Retryable : Outcome {
            override fun toResource(): Resource<UsageData> =
                error("Retryable outcome has no resource — must be retried first")
        }

        fun toResource(): Resource<UsageData>
    }
}
