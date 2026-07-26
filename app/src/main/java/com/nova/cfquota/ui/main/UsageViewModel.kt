package com.nova.cfquota.ui.main

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nova.cfquota.core.Resource
import com.nova.cfquota.data.remote.RequestLog
import com.nova.cfquota.domain.model.CfSettings
import com.nova.cfquota.domain.model.RefreshPrefs
import com.nova.cfquota.domain.usecase.ClearSettingsUseCase
import com.nova.cfquota.domain.usecase.GetResetCountdownUseCase
import com.nova.cfquota.domain.usecase.GetUsageUseCase
import com.nova.cfquota.domain.usecase.ObserveRefreshPrefsUseCase
import com.nova.cfquota.domain.usecase.ObserveSettingsUseCase
import com.nova.cfquota.domain.usecase.SaveSettingsUseCase
import com.nova.cfquota.domain.usecase.SetAutoRefreshUseCase
import com.nova.cfquota.domain.usecase.SetRefreshIntervalUseCase
import com.nova.cfquota.domain.usecase.TestCredentialsUseCase
import com.nova.cfquota.work.WorkScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class UsageViewModel(
    private val getUsage: GetUsageUseCase,
    private val observeSettings: ObserveSettingsUseCase,
    private val saveSettings: SaveSettingsUseCase,
    private val clearSettings: ClearSettingsUseCase,
    private val testCredentials: TestCredentialsUseCase,
    private val getCountdown: GetResetCountdownUseCase,
    private val observeRefreshPrefs: ObserveRefreshPrefsUseCase,
    private val setAutoRefreshPrefs: SetAutoRefreshUseCase,
    private val setRefreshIntervalPrefs: SetRefreshIntervalUseCase,
    private val appContext: Context,
    private val requestJournalFlow: StateFlow<List<RequestLog>>? = null,
    /**
     * Invoked after a successful manual refresh with the freshly fetched data,
     * so the app can push the exact same numbers to every instance of the
     * Glance desktop widget (published through WidgetStore — no second network
     * call). Defaults to a no-op so unit-style usage of the ViewModel keeps
     * working without a widget runtime. Declared `suspend` because the Glance
     * update APIs are suspending.
     */
    private val widgetUpdater: suspend (com.nova.cfquota.domain.model.UsageData) -> Unit = {}
) : ViewModel() {

    private val tag = "CfQuotaVM"

    private val _uiState = MutableStateFlow(UsageUiState(isLoading = true))
    val uiState: StateFlow<UsageUiState> = _uiState.asStateFlow()

    /**
     * Countdown to next reset, isolated from [uiState] so the once-per-second
     * tick only re-renders the countdown banner instead of the entire card.
     */
    private val _countdownSeconds = MutableStateFlow(0L)
    val countdownSeconds: StateFlow<Long> = _countdownSeconds.asStateFlow()

    private val _settings = MutableStateFlow(CfSettings())
    val settings: StateFlow<CfSettings> = _settings.asStateFlow()

    /** Background auto-refresh preferences (independent of credentials). */
    private val _refreshPrefs = MutableStateFlow(RefreshPrefs())
    val refreshPrefs: StateFlow<RefreshPrefs> = _refreshPrefs.asStateFlow()

    private val _testState = MutableStateFlow<TestState>(TestState.Idle)
    val testState: StateFlow<TestState> = _testState.asStateFlow()

    /** Recent request activity (status, latency, body hash) for the diagnostic panel. */
    val requestJournal: StateFlow<List<RequestLog>>? = requestJournalFlow

    /**
     * Tracks the in-flight refresh coroutine so repeated manual clicks cancel
     * the previous network call instead of stacking requests.
     */
    private var refreshJob: Job? = null

    init {
        observeSettingsChanges()
        observeAutoRefreshPrefs()
        startCountdownTicker()
    }

    private fun observeSettingsChanges() {
        observeSettings()
            .onEach { s ->
                val firstLoad = !_settings.value.isConfigured && s.isConfigured
                _settings.value = s
                _uiState.value = _uiState.value.copy(isConfigured = s.isConfigured)
                // Re-evaluate the auto-refresh schedule whenever the configured
                // state flips (e.g. after clearing credentials).
                applyAutoRefresh(_refreshPrefs.value)
                if (s.isConfigured) {
                    if (firstLoad || _uiState.value.data == null) refresh()
                } else {
                    refreshJob?.cancel()
                    refreshJob = null
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        data = null,
                        errorType = null,
                        errorMessage = null,
                        lastUpdatedEpoch = 0L
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeAutoRefreshPrefs() {
        observeRefreshPrefs()
            .onEach { prefs ->
                _refreshPrefs.value = prefs
                applyAutoRefresh(prefs)
            }
            .launchIn(viewModelScope)
    }

    /**
     * Enables/disables the background periodic refresh worker. The worker only
     * runs when auto-refresh is ON and the account is configured; otherwise the
     * scheduled work is cancelled so we never hammer the API with no credentials.
     */
    private fun applyAutoRefresh(prefs: RefreshPrefs) {
        try {
            WorkScheduler.apply(
                context = appContext,
                enabled = prefs.enabled,
                intervalMinutes = prefs.intervalMinutes,
                configured = _settings.value.isConfigured
            )
        } catch (e: Throwable) {
            Log.w(tag, "WorkScheduler.apply failed: ${e.message}")
        }
    }

    /** Emits every second to drive the reset countdown (independent StateFlow). */
    private fun tickerFlow(periodMs: Long = 1000L) = flow {
        while (true) {
            emit(Unit)
            delay(periodMs)
        }
    }

    private fun startCountdownTicker() {
        tickerFlow()
            .onEach { _countdownSeconds.value = getCountdown() }
            .launchIn(viewModelScope)
    }

    /**
     * Fires a single refresh. Cancels any in-flight refresh first so repeated
     * taps never stack concurrent network calls.
     *
     * Returns true if the refresh was actually dispatched, false if it was
     * a duplicate click while a refresh is already in flight (UI uses this
     * for haptic / log clarity).
     */
    fun refresh(): Boolean {
        val s = _settings.value
        if (!s.isConfigured) {
            Log.d(tag, "refresh() skipped: not configured")
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isRefreshing = false,
                isConfigured = false
            )
            return false
        }
        if (refreshJob?.isActive == true) {
            Log.d(tag, "refresh() skipped: previous job still active")
            return false
        }
        val t0 = System.currentTimeMillis()
        Log.d(tag, "refresh() START account=${s.accountId.take(8)}… tokenLen=${s.apiToken.length}")
        refreshJob = viewModelScope.launch {
            val hadData = _uiState.value.data != null
            _uiState.value = _uiState.value.copy(
                isLoading = !hadData,
                isRefreshing = hadData,
                errorType = null,
                errorMessage = null
            )
            when (val result = getUsage(s)) {
                is Resource.Success -> {
                    val d = result.data
                    Log.d(
                        tag,
                        "refresh() OK workers=${d.workersRequests} pages=${d.pagesRequests} " +
                            "total=${d.totalUsed} quota=${d.dailyQuota}"
                    )
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        data = d,
                        errorType = null,
                        errorMessage = null,
                        lastUpdatedEpoch = System.currentTimeMillis()
                    )
                    // Push the freshly-pulled data to the desktop widget too, so
                    // the app UI and the Glance widget never diverge after a refresh.
                    try {
                        widgetUpdater(d)
                    } catch (e: Throwable) {
                        Log.w(tag, "widgetUpdater() failed: ${e.message}")
                    }
                }
                is Resource.Error -> {
                    Log.w(tag, "refresh() ERR type=${result.type} msg=${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorType = result.type,
                        errorMessage = result.message
                        // keep the previous data on screen, but tag the error
                    )
                }
            }
            val dt = System.currentTimeMillis() - t0
            Log.d(tag, "refresh() END ${dt}ms")
            refreshJob = null
        }
        return true
    }

    fun save(newSettings: CfSettings) {
        viewModelScope.launch {
            saveSettings(newSettings)
            _settings.value = newSettings
            _testState.value = TestState.Idle
            refresh()
        }
    }

    /** Clears all persisted credentials & configuration and resets the UI. */
    fun clear() {
        viewModelScope.launch {
            clearSettings()
            _settings.value = CfSettings()
            _testState.value = TestState.Idle
            refreshJob?.cancel()
            refreshJob = null
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isRefreshing = false,
                isConfigured = false,
                data = null,
                errorType = null,
                errorMessage = null,
                lastUpdatedEpoch = 0L
            )
        }
    }

    fun testConnection(candidate: CfSettings) {
        viewModelScope.launch {
            _testState.value = TestState.Testing
            _testState.value = when (val r = testCredentials(candidate)) {
                is Resource.Success -> TestState.Success(r.data.workersRequests + r.data.pagesRequests)
                is Resource.Error -> TestState.Failure(r.message)
            }
        }
    }

    fun resetTestState() {
        _testState.value = TestState.Idle
    }

    // ---- Auto-refresh preferences ----

    fun setAutoRefresh(enabled: Boolean) {
        viewModelScope.launch { setAutoRefreshPrefs(enabled) }
    }

    fun setRefreshInterval(minutes: Int) {
        viewModelScope.launch { setRefreshIntervalPrefs(minutes) }
    }
}

sealed interface TestState {
    data object Idle : TestState
    data object Testing : TestState
    data class Success(val totalToday: Long) : TestState
    data class Failure(val message: String) : TestState
}
