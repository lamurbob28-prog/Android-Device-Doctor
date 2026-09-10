package com.lamurbob28.devicedoctor.v4

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lamurbob28.devicedoctor.BuildConfig

private val Mint = Color(0xFF80E6C4)
private val Amber = Color(0xFFFFD08A)
private val Red = Color(0xFFFFAAA4)
private val Muted = Color(0xFFB2C4CC)
private val Background = Color(0xFF0B151D)
private val Panel = Color(0xFF152630)
private val DoctorColors = darkColorScheme(
    primary = Mint, onPrimary = Color(0xFF00382C), primaryContainer = Color(0xFF184D42),
    onPrimaryContainer = Color(0xFFBDFFE8), secondary = Color(0xFF98CDDF),
    background = Background, onBackground = Color(0xFFE6F1F5),
    surface = Panel, onSurface = Color(0xFFE6F1F5), surfaceVariant = Color(0xFF243741),
    onSurfaceVariant = Muted, outline = Color(0xFF6C858F), error = Red
)

class V4MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DeviceDoctorApp() }
    }
}

@Composable
fun DeviceDoctorApp(vm: DoctorViewModel = viewModel()) {
    val history by vm.history.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val report = vm.latestReport
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var clearDialog by rememberSaveable { mutableStateOf(false) }
    var selectedScan by remember { mutableStateOf<ScanEntity?>(null) }
    var exportText by rememberSaveable { mutableStateOf<String?>(null) }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val text = exportText
        if (uri != null && text != null) vm.saveReport(uri, text)
        exportText = null
    }
    fun save(text: String) {
        exportText = text
        saveFile.launch("device-doctor-${System.currentTimeMillis()}.txt")
    }
    val holder = rememberSaveableStateHolder()
    MaterialTheme(colorScheme = DoctorColors) {
        Scaffold(
            containerColor = Background,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                Row(Modifier.fillMaxWidth().background(Background).statusBarsPadding().padding(horizontal = 22.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(42.dp).background(Mint, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF00382C), modifier = Modifier.size(28.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Device Doctor", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("A clearer picture of your phone", style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                }
            },
            bottomBar = {
                NavigationBar(containerColor = Background, tonalElevation = 0.dp) {
                    val titles = listOf("Overview", "Checks", "Tools", "History")
                    val icons = listOf(Icons.Default.Home, Icons.AutoMirrored.Filled.List, Icons.Default.Build, Icons.Default.DateRange)
                    titles.forEachIndexed { index, title ->
                        NavigationBarItem(selected = tab == index, onClick = { tab = index },
                            icon = { Icon(icons[index], null) }, label = { Text(title, maxLines = 1) },
                            modifier = Modifier.testTag("tab-$index"))
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                    vm.message?.let { message ->
                        Surface(color = Color(0xFF30434D), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(start = 20.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                IconButton(onClick = vm::dismissMessage) { Icon(Icons.Default.Close, "Dismiss message") }
                            }
                        }
                    }
                    holder.SaveableStateProvider(tab) {
                        when (tab) {
                            0 -> Overview(report, vm.isScanning, vm.isClearing, vm::refreshScan, onChecks = { tab = 1 },
                                onSave = { vm.fullReport()?.let { save(it) } },
                                onShare = { vm.fullReport()?.let { shareReport(context, it) } })
                            1 -> ChecksScreen(report, vm.isScanning, vm::refreshScan)
                            2 -> ToolsScreen(vm, onSave = { vm.fullReport()?.let { save(it) } })
                            else -> HistoryScreen(history, vm.isClearing, onOpen = { selectedScan = it }, onClear = { clearDialog = true })
                        }
                    }
                }
            }
        }
        if (clearDialog) AlertDialog(
            onDismissRequest = { clearDialog = false },
            title = { Text("Clear saved scans?") },
            text = { Text("This removes all saved Device Doctor scans and the current report from this app. Export anything you want to keep first. A new scan will only be saved when you scan again.") },
            confirmButton = { TextButton(onClick = { clearDialog = false; vm.clearHistory() }, modifier = Modifier.testTag("confirm-clear")) { Text("Clear scans") } },
            dismissButton = { TextButton(onClick = { clearDialog = false }) { Text("Keep scans") } }
        )
        selectedScan?.let { scan ->
            AlertDialog(onDismissRequest = { selectedScan = null },
                title = { Text("Saved scan") },
                text = { SelectionContainer { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(formatTime(scan.timestamp), color = Mint)
                    Spacer(Modifier.height(14.dp))
                    Text(scan.rawReport, style = MaterialTheme.typography.bodyMedium)
                } } },
                confirmButton = { TextButton(onClick = { selectedScan = null }) { Text("Close") } },
                dismissButton = { Row {
                    TextButton(onClick = { save(scan.rawReport) }) { Text("Save") }
                    TextButton(onClick = { shareReport(context, scan.rawReport) }) { Text("Share") }
                } }
            )
        }
    }
}

@Composable
private fun Overview(report: ScanReport?, scanning: Boolean, clearing: Boolean, onScan: () -> Unit,
                     onChecks: () -> Unit, onSave: () -> Unit, onShare: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("overview-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF1B493F), Color(0xFF15323C))), RoundedCornerShape(28.dp)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("DEVICE SNAPSHOT", color = Mint, style = MaterialTheme.typography.labelLarge, letterSpacing = 2.sp)
                Text(if (scanning && report == null) "Getting your readings…" else report?.scan?.status?.let { statusLabel(it) } ?: "Ready for a checkup",
                    fontSize = 30.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("scan-status"))
                Text(report?.smartSummary ?: "Check battery, storage, connection, and system signals in one place.", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD1E5DD))
                report?.let {
                    Text("${it.findings.count { f -> f.severity == Severity.GOOD }} checks passed · ${it.findings.count { f -> f.needsAttention }} to review", style = MaterialTheme.typography.labelLarge)
                }
                Button(onClick = onScan, enabled = !scanning && !clearing, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("scan-button")) {
                    if (scanning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Refresh, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (scanning) "Scanning…" else "Scan device")
                }
                Text(report?.let { "Captured ${formatTime(it.scan.timestamp)}" } ?: "Runs on your phone · no account needed", style = MaterialTheme.typography.bodySmall, color = Color(0xFFBDD5CD))
            }
        }
        report?.let { r ->
            item {
                SectionTitle("At a glance", "${r.snapshot.manufacturer} ${r.snapshot.model} · Android ${r.scan.androidVersion}")
                Spacer(Modifier.height(12.dp))
                val storage = r.snapshot.storage
                val cards: List<@Composable () -> Unit> = listOf(
                    { MetricCard("BATTERY", batteryLevel(r.snapshot.battery.percent), "${temperatureText(r.snapshot.battery.temperatureC)} · ${r.snapshot.battery.charging}") },
                    { MetricCard("FREE STORAGE", storage?.available?.let { bytes(it) } ?: "Unavailable", storage?.let { "${oneDecimal(it.usedPercent)}% used" } ?: "Reading not provided") },
                    { MetricCard("AVAILABLE RAM", r.snapshot.memory?.available?.let { bytes(it) } ?: "Unavailable", if (r.snapshot.memory?.lowMemory == true) "System reports pressure" else "Managed by Android") },
                    { MetricCard("CONNECTION", r.snapshot.connection?.type ?: "Unavailable", when {
                        r.snapshot.connection == null -> "Reading not provided"
                        !r.snapshot.connection.connected -> "No active network"
                        r.snapshot.connection.captivePortal -> "Sign-in needed"
                        r.snapshot.connection.validated -> "Internet validated"
                        else -> "Internet not verified"
                    }) }
                )
                MetricGrid(cards)
            }
            item {
                val priorities = r.findings.filter { it.needsAttention }.sortedByDescending { it.severity == Severity.BAD }.take(2)
                SectionTitle("Your next steps", if (priorities.isEmpty()) "No alerts in this snapshot" else "Start with these findings")
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    priorities.forEach { FindingCard(it) }
                    if (priorities.isEmpty()) PanelCard { Text("Keep your system and apps updated. Checks show the readings Android exposed; hardware faults may need a manual test.", color = Muted) }
                    OutlinedButton(onClick = onChecks, modifier = Modifier.fillMaxWidth()) { Text("See all ${r.findings.size} checks") }
                }
            }
            item { PanelCard { SectionTitle("Since your last scan"); Text(r.changeSummary, style = MaterialTheme.typography.bodyMedium, color = Muted) } }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Save report") }
                    OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) { Text("Share report") }
                }
                Text("Reports stay on this phone until you choose to export them.", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
    }
}

@Composable
private fun MetricGrid(cards: List<@Composable () -> Unit>) {
    BoxWithConstraints {
        val stacked = maxWidth < 320.dp || LocalDensity.current.fontScale > 1.3f
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (stacked) cards.forEach { it() }
            else cards.chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { content -> Box(Modifier.weight(1f)) { content() } }
            } }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, detail: String) {
    PanelCard {
        Text(label, color = Muted, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
        Text(value, fontSize = 23.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, color = Mint)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
private fun ChecksScreen(report: ScanReport?, scanning: Boolean, onScan: () -> Unit) {
    var category by rememberSaveable { mutableStateOf("All") }
    var attentionOnly by rememberSaveable { mutableStateOf(false) }
    val findings = report?.findings.orEmpty().filter { (category == "All" || it.category.label == category) && (!attentionOnly || it.needsAttention) }
        .sortedBy { when (it.severity) { Severity.BAD -> 0; Severity.WARNING -> 1; Severity.INFO -> 2; Severity.GOOD -> 3 } }
    LazyColumn(Modifier.fillMaxSize().testTag("checks-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("Know what needs attention", "Measured signals, plain explanations, and a useful next step.") }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf("All") + Category.values().map { it.label }).forEach { title ->
                    FilterChip(selected = category == title, onClick = { category = title }, label = { Text(title) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Only show alerts", Modifier.weight(1f))
                Switch(checked = attentionOnly, onCheckedChange = { attentionOnly = it }, modifier = Modifier.semantics { contentDescription = "Only show alerts" })
            }
        }
        if (report == null) item { PanelCard {
            Text(if (scanning) "Reading your device…" else "No scan to show yet.")
            Button(onClick = onScan, enabled = !scanning) { Text("Scan device") }
        } }
        else if (findings.isEmpty()) item { PanelCard { Text("No checks match this filter.", color = Muted) } }
        items(findings, key = { it.title }) { FindingCard(it) }
        item { Text("Alerts use general thresholds and Android status flags. A passing scan is not a complete hardware or malware inspection.", color = Muted, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
fun FindingCard(finding: Finding) {
    val context = LocalContext.current
    PanelCard {
        StatusPill(finding.severity)
        Text(finding.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(finding.detail, style = MaterialTheme.typography.bodyMedium)
        Text(finding.advice, style = MaterialTheme.typography.bodyMedium, color = Muted)
        finding.action?.let { action -> TextButton(onClick = { openSettings(context, action.intentAction) }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)) { Text(action.label) } }
    }
}

@Composable
private fun HistoryScreen(history: List<ScanEntity>, clearing: Boolean, onOpen: (ScanEntity) -> Unit, onClear: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("history-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("Your device over time", "The latest 50 scans stay on this phone. Tap one to read or export it.") }
        if (history.isEmpty()) item { PanelCard {
            Icon(Icons.Default.DateRange, null, tint = Mint, modifier = Modifier.size(32.dp))
            Text("No saved scans", style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("empty-history"))
            Text("Scan your device from Overview to start a baseline.", color = Muted)
        } } else {
            item { StorageTrend(history) }
            items(history, key = { it.id }) { scan ->
                Card(onClick = { onOpen(scan) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(formatTime(scan.timestamp), fontWeight = FontWeight.SemiBold)
                        Text(statusLabel(scan.status), color = when (scan.status) { "BAD" -> Red; "WARNING" -> Amber; else -> Mint })
                        Text("${if (scan.storageUsedPct >= 0) "${oneDecimal(scan.storageUsedPct)}% storage used" else "Storage unavailable"} · ${historicalTemperature(scan)}", color = Muted, style = MaterialTheme.typography.bodyMedium)
                        Text("Patch ${scan.securityPatch} · ${scan.networkType}", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { OutlinedButton(onClick = onClear, enabled = !clearing, modifier = Modifier.fillMaxWidth().testTag("clear-history")) { Text(if (clearing) "Clearing…" else "Clear saved scans") } }
        }
    }
}

@Composable
private fun StorageTrend(history: List<ScanEntity>) {
    val points = history.take(20).reversed().filter { it.storageUsedPct in 0.0..100.0 }
    PanelCard {
        SectionTitle("Storage trend", "Data partition used · oldest → newest")
        if (points.size < 2) Text("A second scan will start the trend.", color = Muted)
        else {
            Text("${oneDecimal(points.first().storageUsedPct)}% → ${oneDecimal(points.last().storageUsedPct)}%", color = Mint)
            Canvas(Modifier.fillMaxWidth().height(86.dp).semantics {
                contentDescription = "Storage usage trend, zero to one hundred percent, across ${points.size} saved scans."
            }) {
                listOf(0f, 0.5f, 1f).forEach { fraction -> drawLine(Color(0xFF344852), androidx.compose.ui.geometry.Offset(0f, size.height * fraction), androidx.compose.ui.geometry.Offset(size.width, size.height * fraction), 1.dp.toPx()) }
                val path = Path()
                points.forEachIndexed { index, scan ->
                    val x = size.width * index / (points.size - 1)
                    val y = size.height * (1 - scan.storageUsedPct.toFloat() / 100)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, Mint, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
            Text("Vertical scale: 0–100% used. Missing readings are omitted.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun PanelCard(content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    subtitle?.let { Text(it, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = Muted) }
}

@Composable
private fun StatusPill(severity: Severity) {
    val color = when (severity) { Severity.GOOD -> Mint; Severity.WARNING -> Amber; Severity.BAD -> Red; Severity.INFO -> Color(0xFF9BC7DB) }
    val text = when (severity) { Severity.GOOD -> "Passed"; Severity.WARNING -> "Worth checking"; Severity.BAD -> "Needs attention"; Severity.INFO -> "Information" }
    Text(text, Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 5.dp), color = color, style = MaterialTheme.typography.labelMedium)
}

fun copyReport(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Device Doctor report", text))
    Toast.makeText(context, "Report copied", Toast.LENGTH_SHORT).show()
}

fun shareReport(context: Context, text: String) {
    try {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Device Doctor report")
            putExtra(Intent.EXTRA_TEXT, text)
        }, "Share report"))
    } catch (_: Exception) { Toast.makeText(context, "No app is available to share this report. Try Save report.", Toast.LENGTH_LONG).show() }
}

fun openSettings(context: Context, action: String) {
    try { context.startActivity(Intent(action)) }
    catch (_: Exception) {
        try { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
        catch (_: Exception) { Toast.makeText(context, "Open Android Settings from your home screen.", Toast.LENGTH_LONG).show() }
    }
}
