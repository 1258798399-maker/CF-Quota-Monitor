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
 *
 * @param loading                 whether a refresh is currently in flight
 * @param usage                   the LATEST [Resource] from the most recent
 *                                refresh attempt (may be `Error`)
 * @param lastSuccess             cached snapshot of the most recent
 *                                [Resource.Success] data — kept on screen
 *                                even if [usage] flips to an [Resource.Error],
 *                                so an auto-refresh hiccup does not wipe the
 *                                user's view of their quota
 * @param updatedAtMillis         epoch millis of the latest completed refresh
 */
data class WidgetState(
    val loading: Boolean = false,
    val usage: Resource<UsageData>? = null,
    val lastSuccess: UsageData? = null,
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
 *
 * v1.6.2 fix: when the fetch ends in [Resource.Error] we *also* preserve
 * [WidgetState.lastSuccess] so the widget can render the previously-good
 * numbers (instead of erasing them and showing nothing but an error message).
 * Without this, an auto-refresh blip every 15 minutes would leave the widget
 * stuck on a misleading "Account ID 错误" hint even after the very next
 * in-app manual refresh succeeded.
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
    val previous = WidgetStore.state.value
    WidgetStore.state.value = previous.copy(loading = true)
    updateAllWidgets(context)
    // 2) Real fetch (same no-cache pipeline as the app + one silent retry on
    //    transient errors — see CloudflareRepositoryImpl), then publish result.
    val result = repo.getTodayUsage(settings)
    val nowLastSuccess = when (result) {
        is Resource.Success -> result.data
        is Resource.Error -> previous.lastSuccess
    }
    WidgetStore.state.value = WidgetState(
        loading = false,
        usage = result,
        lastSuccess = nowLastSuccess,
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
            val nowLastSuccess = when (r) {
                is Resource.Success -> r.data
                is Resource.Error -> seed.lastSuccess
            }
            WidgetStore.state.value = WidgetState(
                loading = false,
                usage = r,
                lastSuccess = nowLastSuccess,
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
                    countdownSeconds = countdown,
                    lastSuccess = state.lastSuccess
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
    countdownSeconds: Long,
    lastSuccess: UsageData?
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

        // v1.6.2 stale-fallback: if the latest fetch errored but we have a
        // previously-cached successful snapshot, show THAT snapshot's numbers
        // instead of clearing the screen and only displaying an error message.
        // The error is still surfaced via a thin banner so the user knows the
        // data may be a few minutes old. This eliminates the v1.6.1 "stuck on
        // Account ID 错误 for 15+ minutes" failure mode (the user's most
        // frequent reported bug at v1.6.2).
        val staleError: Resource.Error? =
            (usage as? Resource.Error)?.takeIf { lastSuccess != null }
        val renderingData: UsageData? = when (val u = usage) {
            is Resource.Success -> u.data
            is Resource.Error -> lastSuccess
            null -> null
        }

        when {
            !configured -> CenterHint("点击打开应用以配置凭据")
            renderingData != null -> {
                staleError?.let { err ->
                    StaleDataBanner(err.type)
                    Spacer(GlanceModifier.height(4.dp))
                }
                StatsContent(renderingData, countdownSeconds)
            }
            usage is Resource.Error -> {
                // No cached fallback yet (cold-start fail). Show the same set
                // of friendly, action-oriented messages as above, mapped from
                // the error type. The misleading v1.6.1 "Account ID 错误"
                // raw backend message is intentionally NOT surfaced —
                // see CloudflareRepositoryImpl for the more accurate text
                // we now generate.
                val msg = when (usage.type) {
                    ErrorType.NETWORK -> "网络异常，点击刷新重试"
                    ErrorType.UNAUTHORIZED -> "凭据无效，请在应用中重新配置"
                    ErrorType.RATE_LIMITED -> "接口限流，请稍后刷新"
                    ErrorType.GRAPHQL -> "获取失败，点击刷新重试"
                    ErrorType.UNKNOWN -> "未知错误，点击刷新重试"
                }
                CenterHint(msg)
            }
            loading -> CenterHint("刷新中…")
            else -> CenterHint("加载中…")
        }
    }
}

/**
 * Renders the three-column stats panel, used both for fresh data and for the
 * stale-data fallback. Pulled out of [WidgetContent] to keep the `when`
 * branches small and legible.
 */
@Composable
private fun StatsContent(d: UsageData, countdownSeconds: Long) {
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

/**
 * Tiny banner shown above the stats when the widget is rendering cached data
 * because the latest fetch errored. Keeps the user informed without erasing
 * their previous view of the quota.
 */
@Composable
private fun StaleDataBanner(lastErrorType: ErrorType) {
    val hint = when (lastErrorType) {
        ErrorType.NETWORK -> "⚠ 网络异常，数据显示可能已过时"
        ErrorType.RATE_LIMITED -> "⚠ 接口限流，数据显示可能已过时"
        ErrorType.GRAPHQL -> "⚠ 获取失败，数据显示可能已过时"
        else -> "⚠ 数据获取异常，点击刷新重试"
    }
    Text(
        text = hint,
        style = TextStyle(fontSize = 11.sp, color = ColorProvider(Orange)),
        modifier = GlanceModifier.fillMaxWidth()
    )
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
