package com.nova.cfquota.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.nova.cfquota.MainActivity
import com.nova.cfquota.core.AppContainer
import com.nova.cfquota.core.Constants
import com.nova.cfquota.core.ErrorType
import com.nova.cfquota.core.Formatters
import com.nova.cfquota.core.Resource
import com.nova.cfquota.domain.model.UsageData
import com.nova.cfquota.domain.usecase.GetResetCountdownUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

private val Brand = Color(0xFF2F65EC)
private val Green = Color(0xFF00A86B)
private val Orange = Color(0xFFF59E0B)

/**
 * Immutable snapshot of what the widget should display.
 */
data class WidgetState(
    val loading: Boolean = false,
    val usage: Resource<UsageData>? = null,
    val updatedAtMillis: Long = 0L
)

/**
 * Process-wide reactive store backing the widget UI.
 *
 * WHY THIS EXISTS: a Glance session runs [GlanceAppWidget.provideGlance] only
 * once per session; subsequent `update()` calls merely recompose the existing
 * composition — code *before* `provideContent` (i.e. a network fetch) is NOT
 * re-executed. The v1.6.0 refresh button relied on exactly that re-execution,
 * which is why it appeared to do nothing. With this store, the UI subscribes
 * via `collectAsState`, and any writer (widget button / background worker /
 * in-app refresh) pushes fresh data through [refreshWidgetData]; recomposition
 * then repaints all widget instances immediately.
 */
object WidgetStore {
    val state = MutableStateFlow(WidgetState())
}

/**
 * Force every widget instance to (re)compose. If a session died, this restarts
 * it; provideGlance will then seed from [WidgetStore] instead of refetching.
 */
suspend fun updateAllWidgets(context: Context) {
    val manager = GlanceAppWidgetManager(context)
    manager.getGlanceIds(CfQuotaWidget::class.java).forEach { glanceId ->
        CfQuotaWidget().update(context, glanceId)
    }
}

/**
 * The single real refresh entry point shared by:
 *  - the widget's own refresh button ([RefreshWidgetAction])
 *  - the background auto-refresh worker
 *  - the in-app manual refresh (via ViewModelFactory's widgetUpdater)
 *
 * Performs the network fetch HERE (not inside provideGlance), publishes a
 * transient loading state first, then the final result. All active widget
 * sessions repaint reactively; dead sessions are woken by [updateAllWidgets].
 */
suspend fun refreshWidgetData(context: Context) {
    val repo = AppContainer(context).repository
    val settings = repo.settings.first()
    if (!settings.isConfigured) {
        WidgetStore.state.value = WidgetState()
        updateAllWidgets(context)
        return
    }
    // 1) Show unmistakable "refreshing" feedback right away.
    WidgetStore.state.value = WidgetStore.state.value.copy(loading = true)
    updateAllWidgets(context)
    // 2) Real fetch (same no-cache pipeline as the app), then publish result.
    val result = repo.getTodayUsage(settings)
    WidgetStore.state.value = WidgetState(
        loading = false,
        usage = result,
        updatedAtMillis = System.currentTimeMillis()
    )
    updateAllWidgets(context)
}

class CfQuotaWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = AppContainer(context)
        val repo = container.repository
        val initialSettings = repo.settings.first()

        // Session start with an empty store (first placement / process restart):
        // do one seed fetch so the widget shows real numbers immediately.
        val seed = WidgetStore.state.value
        if (initialSettings.isConfigured && seed.usage == null && !seed.loading) {
            val r = repo.getTodayUsage(initialSettings)
            WidgetStore.state.value = WidgetState(
                loading = false,
                usage = r,
                updatedAtMillis = System.currentTimeMillis()
            )
        }
        val countdown = GetResetCountdownUseCase().invoke()

        provideContent {
            // Reactive: repaints whenever refreshWidgetData publishes new data,
            // without needing provideGlance to run again.
            val state by WidgetStore.state.collectAsState()
            val settings by repo.settings.collectAsState(initial = initialSettings)
            GlanceTheme {
                WidgetContent(
                    configured = settings.isConfigured,
                    usage = state.usage,
                    loading = state.loading,
                    countdownSeconds = countdown
                )
            }
        }
    }
}

@Composable
private fun WidgetContent(
    configured: Boolean,
    usage: Resource<UsageData>?,
    loading: Boolean,
    countdownSeconds: Long
) {
    val bg = GlanceModifier
        .fillMaxSize()
        .background(GlanceTheme.colors.surface)
        .cornerRadius(16.dp)
        .padding(14.dp)
        // Tapping anywhere on the widget opens the app. The refresh control
        // inside declares its own clickable, which takes precedence in its area.
        .clickable(actionStartActivity<MainActivity>())

    Column(modifier = bg) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Cloudflare 配额",
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurface
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            RefreshButton(loading = loading)
        }
        Spacer(GlanceModifier.height(8.dp))

        when {
            !configured -> CenterHint("点击打开应用以配置凭据")
            usage is Resource.Success -> {
                val d = usage.data
                Text(
                    text = "已用 ${Formatters.thousands(d.totalUsed)} (${Formatters.percent(d.usagePercent)}%)",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = GlanceTheme.colors.onSurface
                    )
                )
                Spacer(GlanceModifier.height(6.dp))
                LinearProgressIndicator(
                    progress = d.fraction,
                    modifier = GlanceModifier.fillMaxWidth().height(10.dp).cornerRadius(6.dp),
                    color = ColorProvider(Brand),
                    backgroundColor = GlanceTheme.colors.surfaceVariant
                )
                Spacer(GlanceModifier.height(10.dp))
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    StatCell("WORKERS", Formatters.thousands(d.workersRequests), Green, GlanceModifier.defaultWeight())
                    StatCell("PAGES", Formatters.thousands(d.pagesRequests), Brand, GlanceModifier.defaultWeight())
                    StatCell("配额", Formatters.thousands(d.dailyQuota), Orange, GlanceModifier.defaultWeight())
                }
                Spacer(GlanceModifier.height(8.dp))
                Text(
                    text = "距重置 ${Formatters.countdown(countdownSeconds)} · ${Constants.RESET_HOUR_BEIJING}:00(UTC+8)",
                    style = TextStyle(fontSize = 11.sp, color = ColorProvider(Orange))
                )
            }
            usage is Resource.Error -> {
                val msg = when (usage.type) {
                    ErrorType.NETWORK -> "网络异常，点击刷新重试"
                    ErrorType.UNAUTHORIZED -> "凭据无效，请在应用中重新配置"
                    ErrorType.RATE_LIMITED -> "接口限流，请稍后刷新"
                    else -> usage.message
                }
                CenterHint(msg)
            }
            loading -> CenterHint("刷新中…")
            else -> CenterHint("加载中…")
        }
    }
}

/**
 * The widget's own refresh control. While a refresh is in flight it shows a
 * spinning progress indicator (clear, unmistakable visual feedback) instead of
 * the plain "刷新" label, so the user always knows the tap registered. Note the
 * data area keeps showing the previous numbers during the refresh — only this
 * corner indicator changes — matching the in-app behaviour.
 */
@Composable
private fun RefreshButton(loading: Boolean) {
    if (loading) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = GlanceModifier.size(14.dp),
                color = ColorProvider(Brand)
            )
            Spacer(GlanceModifier.width(4.dp))
            Text(
                text = "刷新中",
                style = TextStyle(fontSize = 12.sp, color = ColorProvider(Brand))
            )
        }
    } else {
        Text(
            text = "刷新",
            style = TextStyle(fontSize = 12.sp, color = ColorProvider(Brand)),
            modifier = GlanceModifier
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .clickable(actionRunCallback<RefreshWidgetAction>())
        )
    }
}

@Composable
private fun StatCell(label: String, value: String, color: Color, modifier: GlanceModifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onSurfaceVariant)
        )
        Spacer(GlanceModifier.height(2.dp))
        Text(
            text = value,
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ColorProvider(color))
        )
    }
}

@Composable
private fun CenterHint(text: String) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant)
        )
    }
}
