package com.nova.cfquota.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CfQuotaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CfQuotaWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Android does NOT re-render an already-placed Glance widget when the
        // app is updated — the home-screen instance keeps showing the old
        // RemoteViews bitmap, so newly added UI (e.g. the v1.6.3 "最后刷新"
        // line) stays invisible until the user re-adds the widget or taps
        // refresh. Force a re-render of every instance with the freshly
        // installed code. Network is effectively always available right after
        // an install, so the seed fetch inside provideGlance succeeds and the
        // new layout paints immediately — no manual re-add required.
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            CoroutineScope(Dispatchers.IO).launch {
                updateAllWidgets(context)
            }
        }
    }
}

/**
 * Manual refresh from the widget's own button.
 *
 * Delegates to [refreshWidgetData], which performs the actual network fetch
 * and publishes loading → result states through [WidgetStore]. The previous
 * implementation only called `widget.update()` twice and never refetched —
 * Glance does not re-run provideGlance's pre-content code on update(), so the
 * button silently did nothing. This one actually hits the API.
 */
class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        refreshWidgetData(context)
    }
}
