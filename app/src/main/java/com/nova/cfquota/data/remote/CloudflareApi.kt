package com.nova.cfquota.data.remote

import android.util.Log
import com.nova.cfquota.core.Constants
import com.nova.cfquota.data.remote.dto.GraphQlRequest
import com.nova.cfquota.data.remote.dto.GraphQlResponse
import com.nova.cfquota.data.remote.dto.Variables
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Pure OkHttp 4.12 wrapper around the Cloudflare GraphQL Analytics API.
 *
 * Why not Ktor? After v1.3.0 we kept seeing "stale after first request" — the
 * user pressed the refresh button, the UI never updated. Ktor 3.0.3 + OkHttp
 * engine has its own connection pool, keep-alive defaults, and caching layer
 * that are hard to fully disable, and HTTP/2 negotiation on some Android 16
 * stacks interacts badly with CF's edge.
 *
 * The new implementation guarantees every manual refresh hits the network:
 *  - **Cache disabled** (`cache(null)`) + `Cache-Control: no-cache, no-store`
 *    + `Pragma: no-cache` on every outbound request — neither OkHttp nor
 *    Cloudflare's edge can short-circuit with a cached response.
 *  - **Unique per-request `cacheBust`** appended to BOTH the request body and
 *    the `X-Cache-Bust` header, plus a UUID `X-Request-Id` — so even behind a
 *    proxy every refresh produces a byte-distinct, unambiguous request.
 *  - **No in-memory / DataStore usage cache** — `getUsage()` always re-fetches
 *    from the network; only credentials are persisted (encrypted).
 *  - **HttpLoggingInterceptor + custom RequestJournal** so the user can see
 *    exactly what happened on the last N refreshes (status, latency,
 *    response body hash) inside the app — no need for adb logcat.
 */
class CloudflareApi {

    private val tag = "CfApi"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val journalLock = Mutex()
    private val _journal = MutableStateFlow<List<RequestLog>>(emptyList())
    val journal: StateFlow<List<RequestLog>> = _journal.asStateFlow()

    private val httpLogger = HttpLoggingInterceptor { msg ->
        Log.d(tag, msg)
    }.apply {
        level = HttpLoggingInterceptor.Level.HEADERS
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .cache(null)
        .dispatcher(okhttp3.Dispatcher().apply {
            maxRequestsPerHost = 4
        })
        .addInterceptor(httpLogger)
        .build()

    companion object {
        /**
         * Verified LIVE 2026-07 against the Cloudflare GraphQL API.
         *  - `workersInvocationsAdaptive` (NOT `…Groups`) — the `…Groups`
         *    variant does not exist in the current schema.
         *  - `pagesFunctionsInvocationsAdaptiveGroups` — carries Pages
         *    Functions request counts; returns one bucket per call site.
         */
        val QUERY = """
            query GetCloudflareUsage(${'$'}accountTag: String!, ${'$'}dtGeq: String!, ${'$'}dtLeq: String!) {
              viewer {
                accounts(filter: { accountTag: ${'$'}accountTag }) {
                  workersInvocationsAdaptive(
                    filter: { datetime_geq: ${'$'}dtGeq, datetime_leq: ${'$'}dtLeq }
                    limit: 10000
                  ) {
                    sum { requests }
                  }
                  pagesFunctionsInvocationsAdaptiveGroups(
                    filter: { datetime_geq: ${'$'}dtGeq, datetime_leq: ${'$'}dtLeq }
                    limit: 10000
                  ) {
                    sum { requests }
                  }
                }
              }
            }
        """.trimIndent()

        const val JOURNAL_LIMIT = 20
    }

    suspend fun query(
        accountTag: String,
        apiToken: String,
        dtGeq: String,
        dtLeq: String
    ): ApiResult = withContext(Dispatchers.IO) {
        val requestId = UUID.randomUUID().toString()
        val started = System.currentTimeMillis()

        // Unique per-request marker (epoch ms + short uuid). Appended to the
        // request body AND as a header so no proxy / local cache / DataStore
        // layer can ever serve a stale response for a manual refresh.
        val cacheBust = "$started-${requestId.take(8)}"

        val body = json.encodeToString(
            GraphQlRequest.serializer(),
            GraphQlRequest(
                query = QUERY,
                variables = Variables(accountTag, dtGeq, dtLeq),
                cacheBust = cacheBust
            )
        )
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val req = Request.Builder()
            .url(Constants.GRAPHQL_ENDPOINT)
            .post(body.toRequestBody(mediaType))
            .header("Authorization", "Bearer $apiToken")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("User-Agent", "CfQuotaMonitor/1.6.2 (Android)")
            // Force the edge to return the freshest data — defeat any HTTP cache.
            .header("Cache-Control", "no-cache, no-store")
            .header("Pragma", "no-cache")
            // Redundant but belt-and-suspenders cache buster at the header level.
            .header("X-Cache-Bust", cacheBust)
            .header("X-Request-Id", requestId)
            .build()

        var statusCode = 0
        var rawOrErr: String? = null
        var parsed: GraphQlResponse? = null
        var exception: String? = null
        var bodyParseFailed = false

        try {
            client.newCall(req).execute().use { resp ->
                statusCode = resp.code
                val raw = resp.body?.string().orEmpty()
                rawOrErr = raw
                parsed = runCatching { json.decodeFromString<GraphQlResponse>(raw) }
                    .getOrNull()
                // No exception but the body was a 200 with un-parseable JSON
                // (e.g. HTTP/2 stream truncation, empty body, Cloudflare edge
                // hiccup). Without this marker the repository layer would
                // silently misclassify it as "account not found", painting a
                // spurious "Account ID 错误" hint on the home-screen widget —
                // see v1.6.2 release notes. Treat as a transient failure so
                // the caller can retry exactly once.
                bodyParseFailed = (parsed == null && raw.isNotBlank())
            }
        } catch (e: Throwable) {
            exception = "${e.javaClass.simpleName}: ${e.message ?: "<no message>"}"
            Log.w(tag, "request $requestId FAILED: $exception")
        }

        val elapsed = System.currentTimeMillis() - started
        val log = RequestLog(
            requestId = requestId,
            timestampMs = started,
            elapsedMs = elapsed,
            statusCode = statusCode,
            bodyHash = rawOrErr?.let { sha256Short(it) } ?: "—",
            bodyLength = rawOrErr?.length ?: 0,
            error = exception,
            ok = exception == null && statusCode in 200..299 && parsed != null
        )
        appendJournal(log)

        if (exception != null) {
            ApiResult(
                statusCode = 0,
                body = null,
                raw = exception,
                elapsedMs = elapsed,
                requestId = requestId,
                bodyParseFailed = false,
                transientFailure = true
            )
        } else if (bodyParseFailed) {
            // HTTP success status (probably 200) but body could not be decoded
            // into a GraphQlResponse — distinguish from an outright exception.
            // This is almost always a Cloudflare edge blip / HTTP/2 truncation
            // and is exactly the case the repository retry targets.
            Log.w(tag, "request $requestId bodyParseFailed status=$statusCode bodyLen=${rawOrErr?.length ?: 0}")
            ApiResult(
                statusCode = statusCode,
                body = null,
                raw = rawOrErr ?: "",
                elapsedMs = elapsed,
                requestId = requestId,
                bodyParseFailed = true,
                transientFailure = true
            )
        } else {
            ApiResult(
                statusCode = statusCode,
                body = parsed,
                raw = rawOrErr ?: "",
                elapsedMs = elapsed,
                requestId = requestId,
                bodyParseFailed = false,
                transientFailure = false
            )
        }
    }

    private suspend fun appendJournal(entry: RequestLog) = journalLock.withLock {
        val updated = (listOf(entry) + _journal.value).take(JOURNAL_LIMIT)
        _journal.value = updated
    }

    fun clearJournal() {
        _journal.value = emptyList()
    }

    private fun sha256Short(input: String): String {
        if (input.isEmpty()) return "—"
        return runCatching {
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }.take(12)
        }.getOrDefault("err")
    }
}

data class RequestLog(
    val requestId: String,
    val timestampMs: Long,
    val elapsedMs: Long,
    val statusCode: Int,
    val bodyHash: String,
    val bodyLength: Int,
    val error: String?,
    val ok: Boolean
)

data class ApiResult(
    val statusCode: Int,
    val body: GraphQlResponse?,
    val raw: String,
    val elapsedMs: Long = 0L,
    val requestId: String = "",
    /**
     * `true` when the HTTP call returned an OK-ish status (typically 200) but
     * the body could not be decoded as a valid GraphQL response. This is almost
     * always a Cloudflare edge blip / HTTP/2 truncation. Distinct from a thrown
     * exception path (where [transientFailure] is set via `exception != null`
     * AND `body == null`). The repository layer treats both the same way for
     * the purposes of the one-shot retry.
     */
    val bodyParseFailed: Boolean = false,
    /**
     * Aggregated "should the caller retry once?" flag — combines outright
     * network/IO exceptions with [bodyParseFailed]. Authoritative signal for
     * the repository to decide whether to silently retry.
     */
    val transientFailure: Boolean = false
)
