package com.lamurbob28.devicedoctor.v4

import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import com.lamurbob28.devicedoctor.BuildConfig

class DiagnosticsEngine(private val context: Context) {
    fun scan(previous: ScanEntity?): ScanReport = report(collect(), previous)

    private fun collect(): DeviceSnapshot = DeviceSnapshot(
        timestamp = System.currentTimeMillis(),
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        androidVersion = Build.VERSION.RELEASE,
        sdk = Build.VERSION.SDK_INT,
        securityPatch = Build.VERSION.SECURITY_PATCH.orEmpty(),
        battery = runCatching { readBattery() }.getOrDefault(BatteryReading()),
        storage = runCatching {
            val stat = StatFs(context.filesDir.path)
            StorageReading(stat.totalBytes, stat.availableBytes).takeIf { it.total > 0 && it.available in 0..it.total }
        }.getOrNull(),
        memory = runCatching {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }
            MemoryReading(info.totalMem, info.availMem, info.lowMemory).takeIf { it.total > 0 && it.available in 0..it.total }
        }.getOrNull(),
        connection = readConnection(context),
        thermalStatus = if (Build.VERSION.SDK_INT >= 29) runCatching {
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus.takeIf { it in 0..6 }
        }.getOrNull() else null,
        sensorCount = runCatching {
            (context.getSystemService(Context.SENSOR_SERVICE) as SensorManager).getSensorList(Sensor.TYPE_ALL).size
        }.getOrNull(),
        secureLock = runCatching {
            (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure
        }.getOrNull(),
        automaticTime = runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.AUTO_TIME) == 1 }.getOrNull(),
        powerSaver = runCatching {
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode
        }.getOrNull(),
        uptimeMillis = SystemClock.elapsedRealtime()
    )

    private fun readBattery(): BatteryReading {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return BatteryReading()
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        return BatteryReading(
            percent = if (scale > 0 && level in 0..scale) (level.toDouble() / scale * 100).toInt() else null,
            // Negative temperatures are real readings; the presence of the extra determines availability.
            temperatureC = if (intent.hasExtra(BatteryManager.EXTRA_TEMPERATURE)) {
                (intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0).takeIf { it in -50.0..100.0 }
            } else null,
            health = when (intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
                BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                else -> null
            },
            voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1).takeIf { it > 0 },
            charging = when (status) {
                BatteryManager.BATTERY_STATUS_FULL -> "Full"
                BatteryManager.BATTERY_STATUS_CHARGING -> when (plugged) {
                    BatteryManager.BATTERY_PLUGGED_USB -> "Charging · USB"
                    BatteryManager.BATTERY_PLUGGED_AC -> "Charging · adapter"
                    BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Charging · wireless"
                    else -> "Charging"
                }
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "On battery"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
                else -> "Unavailable"
            },
            technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.takeIf { it.isNotBlank() }
        )
    }

    companion object {
        fun report(snapshot: DeviceSnapshot, previous: ScanEntity?): ScanReport {
            val findings = DiagnosticRules.evaluate(snapshot)
            val status = DiagnosticRules.status(findings)
            val score = if (status == "UNKNOWN") -1 else DiagnosticRules.score(findings)
            val attention = findings.count { it.needsAttention }
            val unavailable = listOf(
                DiagnosticRules.patchAgeDays(snapshot.securityPatch, snapshot.timestamp) == null,
                snapshot.battery.temperatureC == null, snapshot.battery.health == null,
                snapshot.storage == null, snapshot.memory == null, snapshot.connection == null,
                snapshot.thermalStatus == null, snapshot.sensorCount == null, snapshot.secureLock == null
            ).count { it }
            val summary = when {
                status == "UNKNOWN" -> "Not enough readings to draw a conclusion. Review the unavailable checks."
                attention == 0 -> "No alerts in the checks Android exposed. This snapshot cannot rule out every device problem."
                else -> "$attention ${if (attention == 1) "check needs" else "checks need"} a closer look. Start with the findings marked Needs attention."
            } + if (unavailable > 0) " $unavailable ${if (unavailable == 1) "reading was" else "readings were"} unavailable." else ""
            val raw = buildString {
                appendLine("Device Doctor ${BuildConfig.VERSION_NAME} · device report")
                appendLine("Captured: ${formatTime(snapshot.timestamp)}")
                appendLine("Device: ${snapshot.manufacturer} ${snapshot.model}")
                appendLine("Android ${snapshot.androidVersion} · API ${snapshot.sdk}")
                appendLine("Security patch: ${snapshot.securityPatch.ifBlank { "Unavailable" }}")
                appendLine("\nSUMMARY\n$summary")
                appendLine("Checklist score: ${if (score < 0) "Unavailable" else "$score/100"} (heuristic; not hardware health or battery capacity)")
                appendLine("\nBATTERY")
                appendLine("Level: ${batteryLevel(snapshot.battery.percent)} · ${snapshot.battery.charging}")
                appendLine("Temperature: ${temperatureText(snapshot.battery.temperatureC)}")
                appendLine("Android condition flag: ${snapshot.battery.health ?: "Unavailable"}")
                appendLine("Voltage: ${snapshot.battery.voltageMv?.let { "$it mV" } ?: "Unavailable"}")
                appendLine("Technology: ${snapshot.battery.technology ?: "Unavailable"}")
                appendLine("System thermal status: ${thermalName(snapshot.thermalStatus)}")
                appendLine("Battery saver: ${snapshot.powerSaver?.let { yesNo(it) } ?: "Unavailable"}")
                appendLine("\nSTORAGE (data partition)")
                snapshot.storage?.let {
                    appendLine("Available: ${bytes(it.available)} · Used: ${bytes(it.used)} · Total: ${bytes(it.total)}")
                } ?: appendLine("Unavailable")
                appendLine("\nMEMORY")
                snapshot.memory?.let {
                    appendLine("Available: ${bytes(it.available)} of ${bytes(it.total)} · Low-memory flag: ${yesNo(it.lowMemory)}")
                } ?: appendLine("Unavailable")
                appendLine("\nCONNECTION")
                snapshot.connection?.let {
                    appendLine("Type: ${it.type} · Validated: ${yesNo(it.validated)}")
                    appendLine("Internet capability: ${yesNo(it.internetCapable)} · Sign-in required: ${yesNo(it.captivePortal)}")
                    appendLine("Metered: ${yesNo(it.metered)} · VPN transport: ${yesNo(it.vpn)}")
                } ?: appendLine("Unavailable")
                appendLine("\nSYSTEM")
                appendLine("Secure screen lock: ${snapshot.secureLock?.let { yesNo(it) } ?: "Unavailable"}")
                appendLine("Automatic time: ${snapshot.automaticTime?.let { yesNo(it) } ?: "Unavailable"}")
                appendLine("Time since restart: ${duration(snapshot.uptimeMillis)}")
                appendLine("Sensor inventory: ${snapshot.sensorCount ?: "Unavailable"}")
                appendLine("\nFINDINGS")
                findings.forEach { appendLine("[${it.severity}] ${it.title}\n${it.detail}\n${it.advice}\n") }
                appendLine("Reports stay in this app until you choose to copy, save, or share them. No unique device identifiers, SSIDs, or IP addresses are collected.")
            }.trim()
            val scan = ScanEntity(
                timestamp = snapshot.timestamp, score = score, status = status,
                androidVersion = snapshot.androidVersion, sdk = snapshot.sdk,
                securityPatch = snapshot.securityPatch.ifBlank { "Unavailable" },
                patchAgeDays = DiagnosticRules.patchAgeDays(snapshot.securityPatch, snapshot.timestamp) ?: -1,
                batteryPercent = snapshot.battery.percent ?: -1,
                batteryTempC = snapshot.battery.temperatureC ?: -273.15,
                batteryHealth = snapshot.battery.health ?: "Unknown",
                storageUsedBytes = snapshot.storage?.used ?: -1,
                storageTotalBytes = snapshot.storage?.total ?: -1,
                storageUsedPct = snapshot.storage?.usedPercent ?: -1.0,
                networkType = snapshot.connection?.type ?: "Unavailable",
                networkValidated = snapshot.connection?.validated ?: false,
                thermalStatus = thermalName(snapshot.thermalStatus),
                uptimeDays = snapshot.uptimeMillis / 86_400_000,
                rawReport = raw
            )
            return ScanReport(scan, findings, summary, changes(previous, scan), raw, snapshot)
        }

        fun changes(previous: ScanEntity?, current: ScanEntity): String = buildString {
            if (previous == null) {
                append("This is your baseline. Scan again after an update or cleanup to compare the readings.")
                return@buildString
            }
            appendLine("Compared with ${formatTime(previous.timestamp)}")
            if (previous.storageUsedBytes >= 0 && current.storageUsedBytes >= 0 && previous.storageTotalBytes == current.storageTotalBytes) {
                appendLine("Storage used: ${signedBytes(current.storageUsedBytes - previous.storageUsedBytes)}")
            } else appendLine("Storage comparison unavailable: a reading is missing or the partition size changed.")
            appendLine("Battery temperature: ${historicalTemperature(previous)} → ${historicalTemperature(current)}")
            appendLine(if (previous.securityPatch == current.securityPatch) "Security patch unchanged: ${current.securityPatch}"
                else "Reported security patch changed: ${previous.securityPatch} → ${current.securityPatch}")
            appendLine("Connection: ${previous.networkType} → ${current.networkType}")
            appendLine("Android internet validation: ${yesNo(previous.networkValidated)} → ${yesNo(current.networkValidated)}")
            append("A patch change records the reported date; an unchanged patch does not prove an update failed.")
        }.trim()
    }
}

fun historicalTemperature(scan: ScanEntity): String =
    if (scan.batteryTempC == -1.0 && !scan.rawReport.startsWith("Device Doctor 5.")) "Unavailable"
    else temperatureText(scan.batteryTempC)

fun readConnection(context: Context): ConnectionReading? = runCatching {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = manager.activeNetwork ?: return@runCatching ConnectionReading()
    val caps = manager.getNetworkCapabilities(network) ?: return@runCatching null
    val vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    ConnectionReading(
        connected = true,
        type = when {
            vpn -> "VPN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Other network"
        },
        validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        internetCapable = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
        captivePortal = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL),
        metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
        vpn = vpn
    )
}.getOrNull()
