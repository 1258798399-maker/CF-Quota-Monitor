package com.nova.cfquota.ui.main

import com.nova.cfquota.core.ErrorType
import com.nova.cfquota.domain.model.UsageData

/**
 * Aggregated state for the main usage screen.
 *
 * `countdownSeconds` lives in its own [kotlinx.coroutines.flow.StateFlow] on the
 * ViewModel (see [UsageViewModel.countdownSeconds]) so the seconds ticker does
 * not force a full main-state recomposition every tick.
 */
data class UsageUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val data: UsageData? = null,
    val errorType: ErrorType? = null,
    val errorMessage: String? = null,
    val isConfigured: Boolean = false,
    val lastUpdatedEpoch: Long = 0L
) {
    val hasError: Boolean get() = errorType != null
}