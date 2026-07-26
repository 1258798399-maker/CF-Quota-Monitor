package com.nova.cfquota.domain.usecase

import com.nova.cfquota.core.Constants
import com.nova.cfquota.core.Resource
import com.nova.cfquota.domain.model.CfSettings
import com.nova.cfquota.domain.model.RefreshPrefs
import com.nova.cfquota.domain.model.UsageData
import com.nova.cfquota.domain.repository.CloudflareRepository
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class GetUsageUseCase(private val repo: CloudflareRepository) {
    suspend operator fun invoke(settings: CfSettings): Resource<UsageData> =
        repo.getTodayUsage(settings)
}

class ObserveSettingsUseCase(private val repo: CloudflareRepository) {
    operator fun invoke(): Flow<CfSettings> = repo.settings
}

class SaveSettingsUseCase(private val repo: CloudflareRepository) {
    suspend operator fun invoke(settings: CfSettings) = repo.saveSettings(settings)
}

class ClearSettingsUseCase(private val repo: CloudflareRepository) {
    suspend operator fun invoke() = repo.clearSettings()
}

class TestCredentialsUseCase(private val repo: CloudflareRepository) {
    suspend operator fun invoke(settings: CfSettings): Resource<UsageData> =
        repo.testCredentials(settings)
}

class ObserveRefreshPrefsUseCase(private val repo: CloudflareRepository) {
    operator fun invoke(): Flow<RefreshPrefs> = repo.refreshPrefs
}

class SetAutoRefreshUseCase(private val repo: CloudflareRepository) {
    suspend operator fun invoke(enabled: Boolean) = repo.setAutoRefresh(enabled)
}

class SetRefreshIntervalUseCase(private val repo: CloudflareRepository) {
    suspend operator fun invoke(minutes: Int) = repo.setRefreshInterval(minutes)
}

/**
 * Computes the remaining time (seconds) until the next 08:00 Beijing-time reset.
 */
class GetResetCountdownUseCase {
    operator fun invoke(now: ZonedDateTime = ZonedDateTime.now(ZoneId.of(Constants.BEIJING_ZONE_ID))): Long {
        val zone = ZoneId.of(Constants.BEIJING_ZONE_ID)
        val current = now.withZoneSameInstant(zone)
        var next = current.withHour(Constants.RESET_HOUR_BEIJING)
            .withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(current)) {
            next = next.plusDays(1)
        }
        return Duration.between(current, next).seconds.coerceAtLeast(0)
    }
}
