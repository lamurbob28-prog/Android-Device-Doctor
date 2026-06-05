package com.lamurbob28.devicedoctor.v4

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class DiagnosticsEngine(private val context: Context) {
    fun scan(previous: ScanEntity?): ScanReport {
        val findings = mutableListOf<Finding>()
        var score = 100

        fun add(severity: Severity, title: String, detail: String, advice: String, penalty: Int = 0) {
            findings += Finding(severity, title, detail, advice)
            score -= penalty
        }

        val patch = Build.VERSION.SECURITY_PATCH ?: "Unknown"
        val patchAge = patchAgeDays(patch)
        when {
            patchAge < 0 -> add(Severity.INFO, "Security Patch", "Security patch date is unavailable.", "Check Android updates manually if this looks wrong.")
            patchAge <= 90 -> add(Severity.GOOD, "Security Patch", "Security patch is recent: $patch.", "Patch age is about $patchAge days.")
            patchAge <= 180 -> add(Severity.WARNING, "Security Patch", "Security patch is aging: $patch.", "Patch age is about $patchAge days. Check for updates.", 5)
            patchAge <= 365 -> add(Severity.WARNING, "Security Patch", "Security patch is stale: $patch.", "Patch age is about $patchAge days. System update check recommended.", 10)
            else -> add(Severity.BAD, "Security Patch", "Security patch is very old: $patch.", "Patch age is about $patchAge days. Update if possible.", 20)
        }

        val thermalName = thermalStatusName(readThermalStatus())
        when (thermalName) {
            "None" -> add(Severity.GOOD, "Thermals", "System thermal status is normal.", "No thermal throttling reported.")
            "Unavailable" -> add(Severity.INFO, "Thermals", "Thermal status is unavailable.", "This Android version may not expose it.")
            "Light" -> add(Severity.WARNING, "Thermals", "System thermal status is light.", "The phone is slightly warm.", 5)
            "Moderate" -> add(Severity.WARNING, "Thermals", "System thermal status is moderate.", "Let the phone cool if performance feels worse.", 10)
            else -> add(Severity.BAD, "Thermals", "System thermal status is $thermalName.", "Stop heavy use and let the phone cool down.", 25)
        }

        val battery = readBattery()
        when {
            battery.tempC < 0 -> add(Severity.INFO, "Battery Temperature", "Temperature is unavailable.", "Android did not expose battery temperature on this scan.")
            battery.tempC >= 45 -> add(Severity.BAD, "Battery Temperature", "Battery is very hot: ${oneDecimal(battery.tempC)} C.", "Stop heavy use and let the phone cool down.", 20)
            battery.tempC >= 40 -> add(Severity.BAD, "Battery Temperature", "Battery is hot: ${oneDecimal(battery.tempC)} C.", "Heat wears batteries down faster.", 15)
            battery.tempC >= 35 -> add(Severity.WARNING, "Battery Temperature", "Battery is warm: ${oneDecimal(battery.tempC)} C.", "Avoid stacking heat with gaming plus charging.", 5)
            else -> add(Severity.GOOD, "Battery Temperature", "Battery temperature looks normal: ${oneDecimal(battery.tempC)} C.", "No heat problem detected.")
        }
        if (battery.health == "Good") add(Severity.GOOD, "Battery Health", "Android reports battery health as good.", "Nothing concerning detected here.")
        else if (battery.health == "Unknown") add(Severity.INFO, "Battery Health", "Battery health is unknown.", "Some devices do not expose useful battery health data.")
        else add(Severity.BAD, "Battery Health", "Android reports battery health as ${battery.health}.", "Watch charging, heat, and sudden shutdowns.", 20)

        val storage = readStorage()
        when {
            storage.usedPct >= 95 -> add(Severity.BAD, "Storage", "Storage is critically full: ${oneDecimal(storage.usedPct)}% used.", "Free space soon.", 30)
            storage.usedPct >= 90 -> add(Severity.BAD, "Storage", "Storage is very full: ${oneDecimal(storage.usedPct)}% used.", "Clear downloads, videos, cache, or unused apps.", 20)
            storage.usedPct >= 80 -> add(Severity.WARNING, "Storage", "Storage is getting high: ${oneDecimal(storage.usedPct)}% used.", "Cleanup is not urgent, but it is getting close.", 10)
            else -> add(Severity.GOOD, "Storage", "Storage looks okay: ${oneDecimal(storage.usedPct)}% used.", "Free space is not currently a problem.")
        }

        val network = readNetwork()
        when {
            !network.hasNetwork -> add(Severity.BAD, "Network", "No active network detected.", "Connect to Wi-Fi or mobile data.", 20)
            !network.hasInternet -> add(Severity.BAD, "Network", "A network exists, but Android does not see internet capability.", "Reconnect or switch networks.", 20)
            !network.validated -> add(Severity.WARNING, "Network", "Connected, but Android has not validated real internet.", "Run Network Doctor or check for a login page.", 10)
            else -> add(Severity.GOOD, "Network", "Internet connection is validated on ${network.type}.", "Network looks normal from Android's view.")
        }
        if (network.hasNetwork && !network.notMetered) add(Severity.INFO, "Metered Network", "This connection may be metered.", "Large downloads may use mobile data or a limited connection.")

        val memory = readMemory()
        if (memory.lowMemory) add(Severity.WARNING, "Memory", "Android reports low memory pressure.", "Close heavy apps or restart if sluggish.", 10)
        else add(Severity.GOOD, "Memory", "Android is not reporting low memory pressure.", "No RAM emergency detected.")

        val sensors = readSensorCount()
        add(Severity.GOOD, "Sensors", "$sensors sensors detected.", "Sensor service is responding normally.")

        val elapsed = SystemClock.elapsedRealtime()
        val uptimeDays = elapsed / 86_400_000L
        when {
            uptimeDays >= 30 -> add(Severity.WARNING, "Uptime", "Phone has been running for $uptimeDays days since boot.", "Restarting can clear background-service problems.", 15)
            uptimeDays >= 14 -> add(Severity.WARNING, "Uptime", "Phone has been running for $uptimeDays days since boot.", "A restart may help if anything feels strange.", 10)
            uptimeDays >= 7 -> add(Severity.WARNING, "Uptime", "Phone has been running for $uptimeDays days since boot.", "A restart can freshen things up.", 5)
            else -> add(Severity.GOOD, "Uptime", "Recent boot: ${duration(elapsed)} since startup.", "No restart recommendation right now.")
        }

        score = score.coerceIn(0, 100)
        val status = if (score >= 85 && findings.none { it.severity == Severity.BAD }) "GOOD" else if (score >= 60) "WARNING" else "BAD"

        val raw = buildRawReport(status, score, patch, patchAge, thermalName, battery, storage, network, memory, sensors, elapsed, findings)
        val entity = ScanEntity(
            timestamp = System.currentTimeMillis(),
            score = score,
            status = status,
            androidVersion = Build.VERSION.RELEASE ?: "Unknown",
            sdk = Build.VERSION.SDK_INT,
            securityPatch = patch,
            patchAgeDays = patchAge,
            batteryPercent = battery.percent,
            batteryTempC = battery.tempC,
            batteryHealth = battery.health,
            storageUsedBytes = storage.used,
            storageTotalBytes = storage.total,
            storageUsedPct = storage.usedPct,
            networkType = network.type,
            networkValidated = network.validated,
            thermalStatus = thermalName,
            uptimeDays = uptimeDays,
            rawReport = raw
        )

        return ScanReport(
            scan = entity,
            findings = findings,
            smartSummary = buildSmartSummary(entity, findings),
            changeSummary = buildChangeSummary(previous, entity),
            rawDetails = raw
        )
    }

    private fun buildRawReport(status: String, score: Int, patch: String, patchAge: Long, thermal: String, battery: BatteryData, storage: StorageData, network: NetworkData, memory: MemoryData, sensors: Int, elapsed: Long, findings: List<Finding>): String {
        val warnings = findings.count { it.severity == Severity.WARNING }
        val bad = findings.count { it.severity == Severity.BAD }
        val good = findings.count { it.severity == Severity.GOOD }
        return buildString {
            appendLine("Device Doctor v4.0 Report")
            appendLine("Scan time: ${formatTime(System.currentTimeMillis())}")
            appendLine()
            appendLine("DEVICE")
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Model: ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE}")
            appendLine("SDK: ${Build.VERSION.SDK_INT}")
            appendLine("Security patch: $patch")
            appendLine("Patch age: ${if (patchAge >= 0) "$patchAge days" else "Unknown"}")
            appendLine()
            appendLine("THERMAL")
            appendLine("Thermal status: $thermal")
            appendLine()
            appendLine("BATTERY")
            appendLine("Level: ${battery.percent}%")
            appendLine("Health: ${battery.health}")
            appendLine("Temperature: ${oneDecimal(battery.tempC)} C")
            appendLine("Voltage: ${battery.voltageMv} mV")
            appendLine("Technology: ${battery.technology}")
            appendLine()
            appendLine("STORAGE")
            appendLine("Internal total: ${bytes(storage.total)}")
            appendLine("Internal used: ${bytes(storage.used)} (${oneDecimal(storage.usedPct)}%)")
            appendLine("Internal free: ${bytes(storage.free)}")
            appendLine()
            appendLine("NETWORK")
            appendLine("Type: ${network.type}")
            appendLine("Internet capability: ${yesNo(network.hasInternet)}")
            appendLine("Validated internet: ${yesNo(network.validated)}")
            appendLine("Not metered: ${yesNo(network.notMetered)}")
            appendLine()
            appendLine("MEMORY")
            appendLine("System available: ${bytes(memory.available)}")
            appendLine("System low memory: ${yesNo(memory.lowMemory)}")
            appendLine()
            appendLine("SENSORS")
            appendLine("Total sensors: $sensors")
            appendLine()
            appendLine("SYSTEM TIME")
            appendLine("Elapsed since boot: ${duration(elapsed)}")
            appendLine()
            appendLine("SUMMARY")
            appendLine("Overall status: $status")
            appendLine("Score: $score/100")
            appendLine("Warnings: $warnings")
            appendLine("Bad issues: $bad")
            appendLine("Good checks: $good")
        }
    }

    private fun buildSmartSummary(scan: ScanEntity, findings: List<Finding>): String {
        val problemFindings = findings.filter { it.severity == Severity.BAD || it.severity == Severity.WARNING }
        return buildString {
            appendLine("Status: ${scan.status}")
            appendLine("Score: ${scan.score}/100")
            appendLine("Scan time: ${formatTime(scan.timestamp)}")
            appendLine()
            if (problemFindings.isEmpty()) appendLine("Problems found: none. The phone looks healthy from this scan.")
            else {
                appendLine("Problems found:")
                problemFindings.forEach { appendLine("- ${it.severity}: ${it.title} - ${it.detail}") }
            }
        }
    }

    private fun buildChangeSummary(previous: ScanEntity?, current: ScanEntity): String {
        if (previous == null) return "No previous Room history yet. Run another scan later and Device Doctor v4 will compare score, storage, battery temperature, network validation, and patch status."
        val scoreDiff = current.score - previous.score
        val storageDiff = current.storageUsedBytes - previous.storageUsedBytes
        return buildString {
            appendLine("Previous scan: ${formatTime(previous.timestamp)}")
            appendLine("Current scan: ${formatTime(current.timestamp)}")
            appendLine()
            appendLine("Score: ${previous.score} -> ${current.score} (${signedInt(scoreDiff)})")
            appendLine("Storage used: ${bytes(previous.storageUsedBytes)} -> ${bytes(current.storageUsedBytes)} (${signedBytes(storageDiff)})")
            appendLine("Battery temp: ${oneDecimal(previous.batteryTempC)} C -> ${oneDecimal(current.batteryTempC)} C")
            if (previous.securityPatch != current.securityPatch) appendLine("Security patch changed: ${previous.securityPatch} -> ${current.securityPatch}")
            else appendLine("Security patch: unchanged (${current.securityPatch})")
            if (previous.networkValidated != current.networkValidated) appendLine("Network validation changed: ${yesNo(previous.networkValidated)} -> ${yesNo(current.networkValidated)}")
            else appendLine("Network validation: unchanged (${yesNo(current.networkValidated)})")
            appendLine()
            if (kotlin.math.abs(scoreDiff) < 3 && kotlin.math.abs(storageDiff) < 200L * 1024L * 1024L) appendLine("Overall: stable since the last scan.")
            else if (scoreDiff > 0) appendLine("Overall: improved since the last scan.")
            else if (scoreDiff < 0) appendLine("Overall: worse than the last scan. Check the warnings above.")
            else appendLine("Overall: mostly unchanged.")
        }
    }

    private fun readBattery(): BatteryData {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (intent == null) return BatteryData()
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
        return BatteryData(
            percent = if (level >= 0 && scale > 0) (level * 100f / scale).toInt() else -1,
            tempC = if (tempTenths >= 0) tempTenths / 10.0 else -1.0,
            voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1),
            health = batteryHealthName(health),
            technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Unknown"
        )
    }

    private fun readStorage(): StorageData {
        val stat = StatFs(Environment.getDataDirectory().path)
        val block = stat.blockSizeLong
        val total = stat.blockCountLong * block
        val free = stat.availableBlocksLong * block
        val used = total - free
        return StorageData(total, used, free, if (total > 0) used * 100.0 / total else 0.0)
    }

    private fun readNetwork(): NetworkData {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return NetworkData()
        val network = cm.activeNetwork ?: return NetworkData()
        val caps = cm.getNetworkCapabilities(network) ?: return NetworkData(hasNetwork = true)
        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Other"
        }
        return NetworkData(true, caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET), caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED), caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED), type)
    }

    private fun readMemory(): MemoryData {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return MemoryData()
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return MemoryData(info.availMem, info.lowMemory)
    }

    private fun readSensorCount(): Int {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return 0
        return sm.getSensorList(Sensor.TYPE_ALL).size
    }

    private fun readThermalStatus(): Int {
        if (Build.VERSION.SDK_INT < 29) return -1
        return try { (context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus } catch (_: Exception) { -1 }
    }

    private fun patchAgeDays(patch: String): Long {
        return try {
            if (patch.length < 10 || patch == "Unknown") return -1
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(patch) ?: return -1
            val diff = System.currentTimeMillis() - date.time
            if (diff < 0) 0 else diff / 86_400_000L
        } catch (_: Exception) { -1 }
    }

    fun bytes(value: Long): String {
        val gb = value / 1024.0 / 1024.0 / 1024.0
        return if (gb >= 1) "${oneDecimal(gb)} GB" else "${oneDecimal(value / 1024.0 / 1024.0)} MB"
    }

    fun formatTime(ms: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ms))
    private fun oneDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)
    private fun yesNo(value: Boolean): String = if (value) "Yes" else "No"
    private fun signedInt(value: Int): String = if (value >= 0) "+$value" else value.toString()
    private fun signedBytes(value: Long): String = if (value >= 0) "+${bytes(value)}" else "-${bytes(abs(value))}"
    private fun duration(ms: Long): String { val s = ms / 1000; return "${s / 86400}d ${(s % 86400) / 3600}h ${(s % 3600) / 60}m" }
    private fun batteryHealthName(h: Int): String = when (h) { BatteryManager.BATTERY_HEALTH_GOOD -> "Good"; BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"; BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"; BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"; BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"; BatteryManager.BATTERY_HEALTH_COLD -> "Cold"; else -> "Unknown" }
    private fun thermalStatusName(t: Int): String = when (t) { PowerManager.THERMAL_STATUS_NONE -> "None"; PowerManager.THERMAL_STATUS_LIGHT -> "Light"; PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"; PowerManager.THERMAL_STATUS_SEVERE -> "Severe"; PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"; PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"; PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"; else -> "Unavailable" }

    private data class BatteryData(val percent: Int = -1, val tempC: Double = -1.0, val voltageMv: Int = -1, val health: String = "Unknown", val technology: String = "Unknown")
    private data class StorageData(val total: Long = 0, val used: Long = 0, val free: Long = 0, val usedPct: Double = 0.0)
    private data class NetworkData(val hasNetwork: Boolean = false, val hasInternet: Boolean = false, val validated: Boolean = false, val notMetered: Boolean = false, val type: String = "None")
    private data class MemoryData(val available: Long = 0, val lowMemory: Boolean = false)
}
