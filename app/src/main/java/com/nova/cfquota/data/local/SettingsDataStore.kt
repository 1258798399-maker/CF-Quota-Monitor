package com.nova.cfquota.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nova.cfquota.core.Constants
import com.nova.cfquota.domain.model.CfSettings
import com.nova.cfquota.domain.model.RefreshPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cf_settings")

/**
 * DataStore-backed settings store. The Account ID and API Token are encrypted
 * at rest with [CryptoManager] (AES/GCM in the Android Keystore).
 */
class SettingsDataStore(private val context: Context) {

    private object Keys {
        val ACCOUNT_ID = stringPreferencesKey("enc_account_id")
        val API_TOKEN = stringPreferencesKey("enc_api_token")
        val DAILY_QUOTA = longPreferencesKey("daily_quota")
        val AUTO_REFRESH = booleanPreferencesKey("auto_refresh_enabled")
        val REFRESH_INTERVAL = intPreferencesKey("refresh_interval_min")
    }

    val settingsFlow: Flow<CfSettings> = context.dataStore.data.map { prefs ->
        CfSettings(
            accountId = CryptoManager.decrypt(prefs[Keys.ACCOUNT_ID] ?: ""),
            apiToken = CryptoManager.decrypt(prefs[Keys.API_TOKEN] ?: ""),
            dailyQuota = prefs[Keys.DAILY_QUOTA] ?: Constants.DEFAULT_DAILY_QUOTA
        )
    }

    /** Background auto-refresh preferences (independent of the credentials). */
    val refreshPrefsFlow: Flow<RefreshPrefs> = context.dataStore.data.map { prefs ->
        RefreshPrefs(
            enabled = prefs[Keys.AUTO_REFRESH] ?: false,
            intervalMinutes = (prefs[Keys.REFRESH_INTERVAL] ?: Constants.DEFAULT_REFRESH_INTERVAL_MIN)
                .coerceAtLeast(Constants.MIN_REFRESH_INTERVAL_MIN)
        )
    }

    suspend fun save(settings: CfSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACCOUNT_ID] = CryptoManager.encrypt(settings.accountId.trim())
            prefs[Keys.API_TOKEN] = CryptoManager.encrypt(settings.apiToken.trim())
            prefs[Keys.DAILY_QUOTA] =
                if (settings.dailyQuota > 0) settings.dailyQuota else Constants.DEFAULT_DAILY_QUOTA
        }
    }

    /** Persists the auto-refresh on/off flag. */
    suspend fun setAutoRefresh(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.AUTO_REFRESH] = enabled }
    }

    /** Persists the auto-refresh interval in minutes (clamped to the floor). */
    suspend fun setRefreshInterval(minutes: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.REFRESH_INTERVAL] = minutes.coerceAtLeast(Constants.MIN_REFRESH_INTERVAL_MIN)
        }
    }

    /** Removes all persisted credentials and restores the default quota. */
    suspend fun clear() {
        context.dataStore.edit { prefs -> prefs.clear() }
    }
}
