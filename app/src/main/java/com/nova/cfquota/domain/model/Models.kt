package com.nova.cfquota.domain.model

/**
 * User configuration persisted (Account ID + API Token are stored encrypted).
 */
data class CfSettings(
    val accountId: String = "",
    val apiToken: String = "",
    val dailyQuota: Long = 100_000L
) {
    val isConfigured: Boolean get() = accountId.isNotBlank() && apiToken.isNotBlank()
}

/**
 * Background auto-refresh preferences, persisted independently of the
 * credential [CfSettings]. `intervalMinutes` is always clamped to a sane
 * minimum by the store, and WorkManager enforces it anyway (15 min floor).
 */
data class RefreshPrefs(
    val enabled: Boolean = false,
    val intervalMinutes: Int = 15
)

/**
 * The daily usage snapshot computed from the Cloudflare GraphQL response.
 */
data class UsageData(
    val workersRequests: Long,
    val pagesRequests: Long,
    val dailyQuota: Long
) {
    val totalUsed: Long get() = workersRequests + pagesRequests

    /** Usage percentage, two decimals. e.g. 46.37 */
    val usagePercent: Double
        get() = if (dailyQuota <= 0) 0.0
        else ((totalUsed.toDouble() / dailyQuota.toDouble()) * 100.0)

    val fraction: Float
        get() = if (dailyQuota <= 0) 0f
        else (totalUsed.toDouble() / dailyQuota.toDouble()).coerceIn(0.0, 1.0).toFloat()
}
