package com.nova.cfquota.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback

class CfQuotaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CfQuotaWidget()
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
