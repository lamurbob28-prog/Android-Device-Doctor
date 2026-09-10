package com.lamurbob28.devicedoctor.v4

import android.content.Intent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lamurbob28.devicedoctor.BuildConfig

@Composable
fun ToolsScreen(vm: DoctorViewModel, onSave: () -> Unit) {
    val context = LocalContext.current
    val network = vm.networkResult
    LazyColumn(Modifier.fillMaxSize().testTag("tools-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionTitle("Put it to the test", "Hands-on checks and shortcuts to the settings that matter.") }
        item {
            PanelCard {
                SectionTitle("Connection test", "Two endpoints. One clear result.")
                Text(network.summary, style = MaterialTheme.typography.bodyMedium)
                if (network.running) LinearProgressIndicator(Modifier.fillMaxWidth())
                network.checks.forEach { check ->
                    HorizontalDivider()
                    Text("${check.name} · ${if (check.success) "Reached" else "Not verified"} · ${check.elapsedMs} ms", style = MaterialTheme.typography.titleSmall)
                    Text(check.detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                network.timestamp?.let { Text("Tested ${formatTime(it)}", style = MaterialTheme.typography.bodySmall) }
                Text("Only runs when you tap. Sends small HTTPS requests to Google and Cloudflare; they can see the connection's public IP. No device report is sent. Uses your current connection, including mobile data or VPN.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                if (network.running) OutlinedButton(onClick = vm::cancelNetworkDoctor, modifier = Modifier.fillMaxWidth()) { Text("Cancel test") }
                else Button(onClick = vm::runNetworkDoctor, modifier = Modifier.fillMaxWidth().testTag("run-network")) { Text("Test connection") }
            }
        }
        item {
            PanelCard {
                SectionTitle("Hardware checkup", "These are manual checks. Observe what happens; the app won't invent a pass or fail.")
                listOf("Touchscreen" to "Trace a grid and check multiple fingers", "Display" to "Inspect solid colors for stuck pixels", "Sound & vibration" to "Play a short tone or vibration", "Live sensors" to "See motion, light, and proximity readings").forEach { (name, detail) ->
                    OutlinedButton(onClick = { context.startActivity(Intent(context, HardwareTestActivity::class.java).putExtra("test", name)) }, modifier = Modifier.fillMaxWidth().testTag("tool-$name"), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(name, style = MaterialTheme.typography.titleSmall)
                            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item {
            PanelCard {
                SectionTitle("Storage cleanup guide")
                Text("1. Review Downloads for old APKs and large files.\n2. Check offline videos, music, and maps in their apps.\n3. Review unused apps in Android Settings.\n4. Run another scan to compare available space.", style = MaterialTheme.typography.bodyMedium)
                Text("Device Doctor measures the data partition. Android restricts access to other apps' private folders; their caches must be managed through Android or the apps themselves.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Button(onClick = { openSettings(context, DoctorAction.STORAGE.intentAction) }) { Text("Manage storage") }
            }
        }
        item {
            PanelCard {
                SectionTitle("Useful settings")
                listOf(DoctorAction.BATTERY, DoctorAction.NETWORK, DoctorAction.UPDATE, DoctorAction.SECURITY).forEach { action ->
                    TextButton(onClick = { openSettings(context, action.intentAction) }, modifier = Modifier.fillMaxWidth()) { Text(action.label) }
                }
            }
        }
        item {
            PanelCard {
                SectionTitle("Take your report with you")
                Text("Save a text file, copy it, or choose where to share it. Review it before sending; it includes the model, Android version, patch date, and diagnostic readings.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onSave, enabled = vm.latestReport != null, modifier = Modifier.fillMaxWidth()) { Text("Save report as a file") }
                OutlinedButton(onClick = { vm.fullReport()?.let { copyReport(context, it) } }, enabled = vm.latestReport != null, modifier = Modifier.fillMaxWidth()) { Text("Copy report") }
            }
        }
        item {
            PanelCard {
                SectionTitle("Device Doctor ${BuildConfig.VERSION_NAME}")
                Text("No account, ads, analytics, background scanning, or automatic cleanup. Up to 50 diagnostic snapshots are kept in this app's storage. Android cloud backup is disabled for this app.", style = MaterialTheme.typography.bodyMedium)
                Text("A device snapshot cannot measure battery wear, inspect every app for malware, or certify hardware. The checklist score in exported reports is a rule-based summary of exposed signals, not a percentage of physical health.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Text("Permissions: network state, internet for the optional connection test, and vibration for the manual check. Saving uses Android's file picker.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
