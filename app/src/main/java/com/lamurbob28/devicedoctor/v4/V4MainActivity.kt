package com.lamurbob28.devicedoctor.v4

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class V4MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DeviceDoctorApp() }
    }
}

@Composable
fun DeviceDoctorApp(vm: DoctorViewModel = viewModel()) {
    val history by vm.history.collectAsState()
    val report = vm.latestReport
    val context = LocalContext.current

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF101114))
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Device Doctor", color = Color(0xFFF1F1F1), fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("v4.0 Kotlin + Compose + Room migration", color = Color(0xFFB6BAC4), fontSize = 15.sp)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.refreshScan() }) { Text("Refresh") }
                Button(onClick = { copyReport(context, vm.fullReport()) }) { Text("Copy") }
                Button(onClick = { shareReport(context, vm.fullReport()) }) { Text("Share") }
            }

            if (report == null) {
                InfoCard("Loading", "Running first v4 scan...")
            } else {
                Dashboard(report)
                InfoCard("Smart Summary", report.smartSummary)
                UpdateVerifier(report, history.drop(1).firstOrNull())
                HistoryCenter(history, onClear = { vm.clearHistory() })
                StorageDoctor(report, history.drop(1).firstOrNull())
                InfoCard("What Changed", report.changeSummary)
                Findings(report.findings)
                NetworkDoctorCard(vm.networkResult.text, vm.networkResult.running, onRun = { vm.runNetworkDoctor() })
                Shortcuts()
                InfoCard("Full Raw Details", report.rawDetails)
            }
        }
    }
}

@Composable
fun Dashboard(report: ScanReport) {
    val scan = report.scan
    InfoCard("V4 Dashboard", buildString {
        appendLine("${scan.status} - ${scan.score}/100")
        append("Patch: ${scan.securityPatch}")
        if (scan.patchAgeDays >= 0) append(" (${scan.patchAgeDays} days old)")
        appendLine()
        appendLine("Storage used: ${oneDecimal(scan.storageUsedPct)}%")
        appendLine("Battery temp: ${oneDecimal(scan.batteryTempC)} C")
        appendLine("Network: ${scan.networkType} / validated: ${yesNo(scan.networkValidated)}")
        appendLine()
        appendLine("Top issues:")
        val issues = report.findings.filter { it.severity == Severity.BAD || it.severity == Severity.WARNING }.take(4)
        if (issues.isEmpty()) appendLine("- No bad or warning-level issues.")
        else issues.forEach { appendLine("- ${it.severity}: ${it.title}") }
    })
}

@Composable
fun UpdateVerifier(report: ScanReport, previous: ScanEntity?) {
    val scan = report.scan
    InfoCard("System Update Verifier", buildString {
        appendLine("Current security patch: ${scan.securityPatch}")
        appendLine(if (scan.patchAgeDays >= 0) "Approx patch age: ${scan.patchAgeDays} days" else "Approx patch age: unavailable")
        if (previous == null) {
            appendLine("Previous scan: none yet")
            appendLine("Run another scan after any update to verify patch changes.")
        } else if (previous.securityPatch != scan.securityPatch) {
            appendLine("Previous patch: ${previous.securityPatch}")
            appendLine("Result: PATCH CHANGED")
        } else {
            appendLine("Previous patch: ${previous.securityPatch}")
            appendLine("Result: patch unchanged since last scan.")
        }
    }, buttonText = "Open System Update", onClick = { openSettings(LocalContext.current, "android.settings.SYSTEM_UPDATE_SETTINGS") })
}

@Composable
fun HistoryCenter(history: List<ScanEntity>, onClear: () -> Unit) {
    InfoCard("History Center", buildString {
        if (history.isEmpty()) appendLine("No Room history yet.")
        else history.take(10).forEachIndexed { index, scan ->
            appendLine("${index + 1}. ${formatTime(scan.timestamp)} | score ${scan.score} | patch ${scan.securityPatch} | storage ${oneDecimal(scan.storageUsedPct)}% | net ${yesNo(scan.networkValidated)}")
        }
    }, buttonText = "Reset History", onClick = onClear)
}

@Composable
fun StorageDoctor(report: ScanReport, previous: ScanEntity?) {
    val scan = report.scan
    InfoCard("Storage Doctor Lite", buildString {
        appendLine("Used storage: ${oneDecimal(scan.storageUsedPct)}%")
        appendLine("Used amount: ${bytes(scan.storageUsedBytes)}")
        if (previous != null) appendLine("Since previous scan: ${signedBytes(scan.storageUsedBytes - previous.storageUsedBytes)}")
        appendLine()
        appendLine("Cleanup checklist:")
        appendLine("- Check Downloads for old APKs, videos, and screenshots.")
        appendLine("- Review apps that store large caches.")
        appendLine("- Move or delete large videos if storage climbs fast.")
        appendLine("- Keep at least 10-15 GB free when possible.")
    }, buttonText = "Open Storage Settings", onClick = { openSettings(LocalContext.current, "android.settings.INTERNAL_STORAGE_SETTINGS") })
}

@Composable
fun Findings(findings: List<Finding>) {
    Text("Diagnosis", color = Color(0xFFF1F1F1), fontSize = 22.sp, fontWeight = FontWeight.Bold)
    val problems = findings.filter { it.severity == Severity.BAD || it.severity == Severity.WARNING }
    if (problems.isEmpty()) InfoCard("No major problems found", "Nothing bad or warning-level showed up in this scan.")
    problems.forEach { FindingCard(it) }
    Text("Other Checks", color = Color(0xFFF1F1F1), fontSize = 20.sp, fontWeight = FontWeight.Bold)
    findings.filter { it.severity == Severity.GOOD || it.severity == Severity.INFO }.forEach { FindingCard(it) }
}

@Composable
fun FindingCard(finding: Finding) {
    InfoCard("${finding.title} - ${finding.severity}", finding.detail + "\n" + finding.advice)
}

@Composable
fun NetworkDoctorCard(text: String, running: Boolean, onRun: () -> Unit) {
    InfoCard("Network Doctor", text, buttonText = if (running) "Running..." else "Run Network Doctor", onClick = onRun)
}

@Composable
fun Shortcuts() {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1D22)), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Useful Shortcuts", color = Color(0xFFF1F1F1), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Button(onClick = { openSettings(context, "android.settings.INTERNAL_STORAGE_SETTINGS") }) { Text("Storage Settings") }
            Button(onClick = { openSettings(context, "android.settings.BATTERY_SAVER_SETTINGS") }) { Text("Battery Settings") }
            Button(onClick = { openSettings(context, "android.settings.WIFI_SETTINGS") }) { Text("Wi-Fi Settings") }
            Button(onClick = { openSettings(context, "android.settings.SYSTEM_UPDATE_SETTINGS") }) { Text("System Update") }
        }
    }
}

@Composable
fun InfoCard(title: String, body: String, buttonText: String? = null, onClick: (() -> Unit)? = null) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1D22)), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, color = Color(0xFFF1F1F1), fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text(body.trim(), color = Color(0xFFB6BAC4), fontSize = 14.sp)
            if (buttonText != null && onClick != null) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onClick) { Text(buttonText) }
            }
        }
    }
}

fun copyReport(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Device Doctor Report", text))
    Toast.makeText(context, "Report copied", Toast.LENGTH_SHORT).show()
}

fun shareReport(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Device Doctor Report")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Share Device Doctor Report"))
}

fun openSettings(context: Context, action: String) {
    try { context.startActivity(Intent(action)) }
    catch (_: Exception) { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
}

fun formatTime(ms: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ms))
fun oneDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)
fun yesNo(value: Boolean): String = if (value) "Yes" else "No"
fun bytes(value: Long): String { val gb = value / 1024.0 / 1024.0 / 1024.0; return if (gb >= 1) "${oneDecimal(gb)} GB" else "${oneDecimal(value / 1024.0 / 1024.0)} MB" }
fun signedBytes(value: Long): String = if (value >= 0) "+${bytes(value)}" else "-${bytes(kotlin.math.abs(value))}"
