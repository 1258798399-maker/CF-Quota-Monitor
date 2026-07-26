package com.nova.cfquota.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.cfquota.core.ErrorType
import com.nova.cfquota.core.Formatters
import com.nova.cfquota.ui.main.UsageUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The main Cloudflare usage card, faithfully following the design spec:
 * header + capsule progress bar + 3-column stat panel + countdown banner.
 *
 * `countdownSeconds` is passed in from the ViewModel so the seconds ticker
 * does not force a recomposition of the data widgets above.
 */
@Composable
fun UsageCard(
    state: UsageUiState,
    countdownSeconds: Long,
    onRefresh: () -> Unit,
    onConfigure: () -> Unit,
    onShowLogs: () -> Unit,
    requestJournalSize: Int = 0,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            CardHeader(
                isRefreshing = state.isRefreshing,
                lastUpdatedEpoch = state.lastUpdatedEpoch,
                onRefresh = onRefresh,
                onConfigure = onConfigure,
                onShowLogs = onShowLogs,
                requestJournalSize = requestJournalSize
            )
            Spacer(Modifier.height(16.dp))

            when {
                !state.isConfigured && !state.isLoading -> {
                    EmptyConfigState(onConfigure = onConfigure)
                }
                state.isLoading -> {
                    LoadingState()
                }
                state.hasError && state.data == null -> {
                    ErrorState(
                        type = state.errorType ?: ErrorType.UNKNOWN,
                        message = state.errorMessage ?: "未知错误",
                        onRetry = onRefresh,
                        onConfigure = onConfigure
                    )
                }
                state.data != null -> {
                    val d = state.data
                    CapsuleProgressBar(
                        fraction = d.fraction,
                        label = "请求使用进度: ${Formatters.thousands(d.totalUsed)} (${Formatters.percent(d.usagePercent)}%)"
                    )
                    Spacer(Modifier.height(16.dp))
                    StatPanel(
                        workersText = Formatters.thousands(d.workersRequests),
                        pagesText = Formatters.thousands(d.pagesRequests),
                        quotaText = Formatters.thousands(d.dailyQuota)
                    )
                    Spacer(Modifier.height(16.dp))
                    CountdownBanner(
                        countdownText = Formatters.countdown(countdownSeconds),
                        totalUsedText = Formatters.thousands(d.totalUsed)
                    )
                    if (state.hasError) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "刷新失败：${state.errorMessage}",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardHeader(
    isRefreshing: Boolean,
    lastUpdatedEpoch: Long,
    onRefresh: () -> Unit,
    onConfigure: () -> Unit,
    onShowLogs: () -> Unit,
    requestJournalSize: Int
) {
    var menuExpanded by remember { mutableStateOf(false) }

    // Continuous 360° rotation while isRefreshing is true. Stops instantly
    // when false so the user gets clear visual feedback of the network call.
    val infiniteTransition = rememberInfiniteTransition(label = "refresh-rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "rot"
    )

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Workers/Pages 请求使用情况",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onRefresh,
                    enabled = !isRefreshing
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = if (isRefreshing) "正在刷新" else "刷新",
                        tint = if (isRefreshing)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer {
                                rotationZ = if (isRefreshing) rotation else 0f
                            }
                    )
                }
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp)
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (isRefreshing) "正在刷新…" else "手动刷新") },
                        onClick = {
                            menuExpanded = false
                            if (!isRefreshing) onRefresh()
                        },
                        leadingIcon = { Icon(Icons.Default.Refresh, null) },
                        enabled = !isRefreshing
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (requestJournalSize > 0) "请求日志（$requestJournalSize）"
                                else "请求日志"
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onShowLogs()
                        },
                        leadingIcon = { Icon(Icons.Default.History, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("配置凭据") },
                        onClick = {
                            menuExpanded = false
                            onConfigure()
                        },
                        leadingIcon = { Icon(Icons.Default.Settings, null) }
                    )
                }
            }
        }
        if (lastUpdatedEpoch > 0L) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "最后刷新：${formatClock(lastUpdatedEpoch)}" +
                    if (isRefreshing) " · 正在获取最新数据…" else "",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        } else if (isRefreshing) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "正在获取数据…",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
        }
    }
}

private val TIME_FMT = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

private fun formatClock(epochMs: Long): String =
    TIME_FMT.format(Date(epochMs))