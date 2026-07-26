package com.nova.cfquota.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.cfquota.core.Constants
import com.nova.cfquota.core.Formatters
import com.nova.cfquota.domain.model.CfSettings
import com.nova.cfquota.ui.main.TestState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    current: CfSettings,
    testState: TestState,
    onDismiss: () -> Unit,
    onTest: (CfSettings) -> Unit,
    onSave: (CfSettings) -> Unit,
    onClear: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showClearConfirm by remember { mutableStateOf(false) }

    var accountId by remember(current) { mutableStateOf(current.accountId) }
    var apiToken by remember(current) { mutableStateOf(current.apiToken) }
    var quotaText by remember(current) {
        mutableStateOf(
            (if (current.dailyQuota > 0) current.dailyQuota else Constants.DEFAULT_DAILY_QUOTA).toString()
        )
    }
    var tokenVisible by remember { mutableStateOf(false) }

    fun buildCandidate(): CfSettings = CfSettings(
        accountId = accountId.trim(),
        apiToken = apiToken.trim(),
        dailyQuota = quotaText.trim().toLongOrNull()?.takeIf { it > 0 }
            ?: Constants.DEFAULT_DAILY_QUOTA
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 20.dp)
        ) {
            Text(
                text = "配置 Cloudflare 凭据",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "凭据将通过 Android Keystore 加密后存储在本地设备。",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = accountId,
                onValueChange = { accountId = it },
                label = { Text("Account ID") },
                placeholder = { Text("32 位账户标识") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = apiToken,
                onValueChange = { apiToken = it },
                label = { Text("API Token") },
                placeholder = { Text("Bearer Token（需 Analytics 读取权限）") },
                singleLine = true,
                visualTransformation = if (tokenVisible) androidx.compose.ui.text.input.VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    androidx.compose.material3.TextButton(
                        onClick = { tokenVisible = !tokenVisible }
                    ) {
                        Text(
                            text = if (tokenVisible) "隐藏" else "显示",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = quotaText,
                onValueChange = { v -> quotaText = v.filter { it.isDigit() } },
                label = { Text("每日配额") },
                placeholder = { Text("默认 100000") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            // Test result feedback
            Spacer(Modifier.height(12.dp))
            when (testState) {
                is TestState.Testing -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        "  正在测试连接…",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                is TestState.Success -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "  连接成功！今日已用 ${Formatters.thousands(testState.totalToday)} 次",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is TestState.Failure -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "  ${testState.message}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                TestState.Idle -> {}
            }

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { onTest(buildCandidate()) },
                    enabled = accountId.isNotBlank() && apiToken.isNotBlank() &&
                        testState !is TestState.Testing,
                    modifier = Modifier.weight(1f)
                ) { Text("测试连接") }
                Button(
                    onClick = { onSave(buildCandidate()) },
                    enabled = accountId.isNotBlank() && apiToken.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text("保存") }
            }

            if (current.isConfigured) {
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.TextButton(
                    onClick = { showClearConfirm = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "  清除当前配置",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清除当前配置？", fontWeight = FontWeight.Bold) },
            text = {
                Text("将删除本地保存的 Account ID、API Token 及自定义配额，且不可恢复。清除后需重新配置才能继续监控。")
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showClearConfirm = false
                        onClear()
                    }
                ) {
                    Text("确认清除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showClearConfirm = false }
                ) { Text("取消") }
            }
        )
    }
}
