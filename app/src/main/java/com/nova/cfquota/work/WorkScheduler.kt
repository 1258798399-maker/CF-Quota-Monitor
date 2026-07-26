package com.nova.cfquota.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Constraints
import java.util.concurrent.TimeUnit

/**
 * Owns the single unique periodic work that drives background auto-refresh.
 *
 * WorkManager enforces a 15-minute minimum for periodic work, which dovetails
 * nicely with the app's "minimum 15 minutes" requirement. We clamp the user
 * interval to that floor and schedule a CONNECTED-only worker that re-pulls the
 * usage and pushes it to the Glance widget.
 */
object WorkScheduler {

    private const val UNIQUE_NAME = "cfquota_auto_refresh"

    fun apply(
        context: Context,
        enabled: Boolean,
        intervalMinutes: Int,
        configured: Boolean
    ) {
        val wm = WorkManager.getInstance(context)
        if (!enabled || !configured) {
            wm.cancelUniqueWork(UNIQUE_NAME)
            return
        }
        val minutes = intervalMinutes.coerceAtLeast(15).toLong()
        val request = PeriodicWorkRequest.Builder(
            RefreshWorker::class.java,
            minutes,
            TimeUnit.MINUTES
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        ).build()

        wm.enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
    }
}
