package com.nova.cfquota.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nova.cfquota.core.AppContainer
import com.nova.cfquota.core.Resource
import com.nova.cfquota.widget.WidgetState
import com.nova.cfquota.widget.WidgetStore
import com.nova.cfquota.widget.updateAllWidgets

@Suppress("UNCHECKED_CAST")
class UsageViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UsageViewModel::class.java)) {
            return UsageViewModel(
                getUsage = container.getUsageUseCase,
                observeSettings = container.observeSettingsUseCase,
                saveSettings = container.saveSettingsUseCase,
                clearSettings = container.clearSettingsUseCase,
                testCredentials = container.testCredentialsUseCase,
                getCountdown = container.getResetCountdownUseCase,
                observeRefreshPrefs = container.observeRefreshPrefsUseCase,
                setAutoRefreshPrefs = container.setAutoRefreshUseCase,
                setRefreshIntervalPrefs = container.setRefreshIntervalUseCase,
                appContext = container.appContext,
                requestJournalFlow = container.requestJournal,
                // After a successful in-app refresh, publish the exact data the
                // app just fetched into the widget's reactive store (no second
                // network call), then poke every Glance instance so any dead
                // session restarts and picks the fresh state up.
                //
                // v1.6.2: we also set `lastSuccess = data` here so the widget's
                // stale-data fallback has a fresh "last good snapshot" to
                // render if the next auto-refresh fails. Resetting lastSuccess
                // to null would defeat the entire purpose of the v1.6.2 fix.
                widgetUpdater = { data ->
                    WidgetStore.state.value = WidgetState(
                        loading = false,
                        usage = Resource.Success(data),
                        lastSuccess = data,
                        updatedAtMillis = System.currentTimeMillis()
                    )
                    updateAllWidgets(container.appContext)
                }
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
