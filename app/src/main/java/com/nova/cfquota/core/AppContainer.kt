package com.nova.cfquota.core

import android.content.Context
import com.nova.cfquota.data.local.SettingsDataStore
import com.nova.cfquota.data.remote.CloudflareApi
import com.nova.cfquota.data.repository.CloudflareRepositoryImpl
import com.nova.cfquota.domain.repository.CloudflareRepository
import com.nova.cfquota.domain.usecase.ClearSettingsUseCase
import com.nova.cfquota.domain.usecase.GetResetCountdownUseCase
import com.nova.cfquota.domain.usecase.GetUsageUseCase
import com.nova.cfquota.domain.usecase.ObserveRefreshPrefsUseCase
import com.nova.cfquota.domain.usecase.ObserveSettingsUseCase
import com.nova.cfquota.domain.usecase.SaveSettingsUseCase
import com.nova.cfquota.domain.usecase.SetAutoRefreshUseCase
import com.nova.cfquota.domain.usecase.SetRefreshIntervalUseCase
import com.nova.cfquota.domain.usecase.TestCredentialsUseCase

/**
 * Lightweight manual dependency container (no third-party DI to keep the build lean).
 */
class AppContainer(context: Context) {
    /** Exposed so the ViewModel can drive a widget refresh after a successful pull. */
    val appContext = context.applicationContext

    private val api: CloudflareApi by lazy { CloudflareApi() }
    private val dataStore: SettingsDataStore by lazy { SettingsDataStore(appContext) }

    val repository: CloudflareRepository by lazy {
        CloudflareRepositoryImpl(api, dataStore)
    }

    val getUsageUseCase by lazy { GetUsageUseCase(repository) }
    val observeSettingsUseCase by lazy { ObserveSettingsUseCase(repository) }
    val saveSettingsUseCase by lazy { SaveSettingsUseCase(repository) }
    val clearSettingsUseCase by lazy { ClearSettingsUseCase(repository) }
    val testCredentialsUseCase by lazy { TestCredentialsUseCase(repository) }
    val observeRefreshPrefsUseCase by lazy { ObserveRefreshPrefsUseCase(repository) }
    val setAutoRefreshUseCase by lazy { SetAutoRefreshUseCase(repository) }
    val setRefreshIntervalUseCase by lazy { SetRefreshIntervalUseCase(repository) }
    val getResetCountdownUseCase by lazy { GetResetCountdownUseCase() }

    /** Expose the API's request journal so the UI can render recent activity. */
    val requestJournal get() = api.journal
}
