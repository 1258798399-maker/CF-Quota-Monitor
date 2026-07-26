package com.nova.cfquota

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nova.cfquota.ui.main.MainScreen
import com.nova.cfquota.ui.main.UsageViewModel
import com.nova.cfquota.ui.main.UsageViewModelFactory
import com.nova.cfquota.ui.theme.CfQuotaTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: UsageViewModel by viewModels {
        UsageViewModelFactory((application as CfQuotaApp).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 16 / Edge-to-Edge: draw behind the system bars.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        try {
            setContent {
                CfQuotaTheme {
                    // Refresh whenever the app returns to the foreground.
                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                viewModel.refresh()
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }
                    MainScreen(viewModel = viewModel)
                }
            }
        } catch (e: Throwable) {
            // A crash during first composition would otherwise just flash a
            // white screen and the user gets no information. Surface it.
            Log.e("CfQuotaApp", "FATAL during setContent", e)
            writeCrashLog(e)
            try {
                setContent { CrashScreen(e) }
            } catch (_: Throwable) {
                // Last resort: nothing we can do in Compose.
            }
        }
    }

    private fun writeCrashLog(e: Throwable) {
        try {
            val file = File(filesDir, "crash.log")
            file.writeText(
                "Crash at ${System.currentTimeMillis()}\n" +
                    Log.getStackTraceString(e)
            )
        } catch (_: Throwable) {
            // ignore
        }
    }
}

@Composable
private fun CrashScreen(e: Throwable) {
    CfQuotaTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "应用启动出错",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = e.message ?: e.javaClass.simpleName,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "请将以下信息反馈给开发者：\n${e.stackTraceToString().take(1000)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
