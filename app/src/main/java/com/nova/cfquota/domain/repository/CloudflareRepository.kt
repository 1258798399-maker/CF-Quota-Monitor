package com.nova.cfquota.domain.repository

import com.nova.cfquota.core.Resource
import com.nova.cfquota.domain.model.CfSettings
import com.nova.cfquota.domain.model.RefreshPrefs
import com.nova.cfquota.domain.model.UsageData
import kotlinx.coroutines.flow.Flow

interface CloudflareRepository {

    /** Reactive stream of the persisted settings. */
    val settings: Flow<CfSettings>

    suspend fun saveSettings(settings: CfSettings)

    /** Clears all persisted credentials & quota configuration. */
    suspend fun clearSettings()

    /** Fetch today's usage using the given (or persisted) credentials. */
    suspend fun getTodayUsage(settings: CfSettings): Resource<UsageData>

    /** Lightweight credential validity check. */
    suspend fun testCredentials(settings: CfSettings): Resource<UsageData>

    /** Reactive stream of background auto-refresh preferences. */
    val refreshPrefs: Flow<RefreshPrefs>

    /** Enable / disable the background periodic auto-refresh. */
    suspend fun setAutoRefresh(enabled: Boolean)

    /** Set the auto-refresh interval in minutes (clamped to a sane minimum). */
    suspend fun setRefreshInterval(minutes: Int)
}
