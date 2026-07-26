package com.nova.cfquota.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.cfquota.data.remote.RequestLog
import com.nova.cfquota.ui.components.UsageCard
import com.nova.cfquota.ui.settings.SettingsScreen
import com.nova.cfquota.ui.settings.SettingsSheet
import kotlinx.coroutines.flow.StateFlow

@Composable
fun MainScreen(viewModel: UsageViewModel) {
    val state by viewModel.uiState.collectAsState()
    val countdown by viewModel.countdownSeconds.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val testState by viewModel.testState.collectAsState()
    val journal: List<RequestLog> = viewModel.requestJournal
        ?.collectAsState()?.value ?: emptyList()
    var screen by remember { mutableStateOf(Screen.MAIN) }
    var showSheet by remember { mutableStateOf(false) }
    var showLogSheet by remember { mutableStateOf(false) }

    if (screen == Screen.SETTINGS) {
        SettingsScreen(viewModel = viewModel, onBack = { screen = Screen.MAIN })
    } else {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cloudflare 配额监控",
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 12.dp)
                    )
                    IconButton(onClick = { screen = Screen.SETTINGS }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                UsageCard(
                    state = state,
                    countdownSeconds = countdown,
                    requestJournalSize = journal.size,
                    onRefresh = viewModel::refresh,
                    onConfigure = { showSheet = true },
                    onShowLogs = { showLogSheet = true }
                )
                Spacer(Modifier.height(24.dp))
            }
        }

        if (showSheet) {
            SettingsSheet(
                current = settings,
                testState = testState,
                onDismiss = {
                    showSheet = false
                    viewModel.resetTestState()
                },
                onTest = viewModel::testConnection,
                onSave = {
                    viewModel.save(it)
                    showSheet = false
                },
                onClear = {
                    viewModel.clear()
                    showSheet = false
                }
            )
        }

        if (showLogSheet) {
            RequestLogSheet(
                logs = journal,
                onDismiss = { showLogSheet = false }
            )
        }
    }
}

private enum class Screen { MAIN, SETTINGS }
