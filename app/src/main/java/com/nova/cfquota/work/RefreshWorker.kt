package com.nova.cfquota.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nova.cfquota.core.AppContainer
import com.nova.cfquota.widget.refreshWidgetData
import kotlinx.coroutines.flow.first

/**
 * Background refresh worker. Reads the persisted credentials, and if configured,
 * performs a real network fetch via [refreshWidgetData], which publishes the
 * result through WidgetStore so every widget instance repaints. This keeps the
 * home-screen widget in lock-step with the in-app data, on the user-selected
 * interval. (Note: merely calling widget.update() would NOT refetch — Glance
 * does not re-run provideGlance on update.)
 */
class RefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = AppContainer(applicationContext)
        val repo = container.repository
        val settings = repo.settings.first()
        if (!settings.isConfigured) {
            // No point polling without credentials; report success so WorkManager
            // does not retry forever.
            return Result.success()
        }
        return try {
            refreshWidgetData(applicationContext)
            Result.success()
        } catch (e: Throwable) {
            Result.retry()
        }
    }
}
