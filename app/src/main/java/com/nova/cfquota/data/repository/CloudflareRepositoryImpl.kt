package com.nova.cfquota.data.repository

import com.nova.cfquota.core.Constants
import com.nova.cfquota.core.ErrorType
import com.nova.cfquota.core.Resource
import com.nova.cfquota.data.local.SettingsDataStore
import com.nova.cfquota.data.remote.CloudflareApi
import com.nova.cfquota.domain.model.CfSettings
import com.nova.cfquota.domain.model.RefreshPrefs
import com.nova.cfquota.domain.model.UsageData
import com.nova.cfquota.domain.repository.CloudflareRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.IOException

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
            try {
                val result = api.query(
                    accountTag = settings.accountId,
                    apiToken = settings.apiToken,
                    dtGeq = dtGeq,
                    dtLeq = dtLeq
                )

                when (result.statusCode) {
                    401, 403 -> return@withContext Resource.Error(
                        ErrorType.UNAUTHORIZED, "API Token 无效或权限不足 (${result.statusCode})"
                    )
                    429 -> return@withContext Resource.Error(
                        ErrorType.RATE_LIMITED, "请求过于频繁，已触发接口限流 (429)"
                    )
                }

                val body = result.body
                if (body?.errors?.isNotEmpty() == true) {
                    val msg = body.errors.firstOrNull()?.message ?: "GraphQL 查询失败"
                    val type = if (msg.contains("authentication", true) ||
                        msg.contains("Unauthorized", true)
                    ) ErrorType.UNAUTHORIZED else ErrorType.GRAPHQL
                    return@withContext Resource.Error(type, msg)
                }

                val account = body?.data?.viewer?.accounts?.firstOrNull()
                if (account == null) {
                    return@withContext Resource.Error(
                        ErrorType.GRAPHQL,
                        "未找到账户数据，请检查 Account ID 是否正确"
                    )
                }

                // Live-verified: workersInvocationsAdaptive => WORKERS requests;
                // pagesFunctionsInvocationsAdaptiveGroups => PAGES requests.
                val workers = account.workersGroups.sumOf { it.sum?.requests ?: 0L }
                val pages = account.pagesGroups.sumOf { it.sum?.requests ?: 0L }

                Resource.Success(
                    UsageData(
                        workersRequests = workers,
                        pagesRequests = pages,
                        dailyQuota = if (settings.dailyQuota > 0) settings.dailyQuota
                        else Constants.DEFAULT_DAILY_QUOTA
                    )
                )
            } catch (e: IOException) {
                Resource.Error(ErrorType.NETWORK, "网络连接异常，请检查网络后重试")
            } catch (e: Exception) {
                Resource.Error(ErrorType.UNKNOWN, e.message ?: "未知错误")
            }
        }
}
