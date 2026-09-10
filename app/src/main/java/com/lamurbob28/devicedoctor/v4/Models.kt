package com.lamurbob28.devicedoctor.v4

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class Severity { GOOD, WARNING, BAD, INFO }
enum class Category(val label: String) {
    BATTERY("Battery"), STORAGE("Storage"), NETWORK("Connection"), SYSTEM("System")
}
enum class DoctorAction(val label: String, val intentAction: String) {
    STORAGE("Manage storage", "android.settings.INTERNAL_STORAGE_SETTINGS"),
    BATTERY("Battery settings", "android.settings.BATTERY_SAVER_SETTINGS"),
    NETWORK("Connection settings", "android.settings.WIRELESS_SETTINGS"),
    UPDATE("Check system updates", "android.settings.SYSTEM_UPDATE_SETTINGS"),
    SECURITY("Screen lock settings", "android.settings.SECURITY_SETTINGS"),
    DATE("Date & time settings", "android.settings.DATE_SETTINGS")
}

data class Finding(
    val severity: Severity,
    val title: String,
    val detail: String,
    val advice: String,
    val category: Category = Category.SYSTEM,
    val action: DoctorAction? = null,
    val penalty: Int = 0
) {
    val needsAttention: Boolean get() = severity == Severity.WARNING || severity == Severity.BAD
}

// Keep the v4 table and database identity: updating the app does not discard saved scans.
@Entity(tableName = "scans")
data class ScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val score: Int,
    val status: String,
    val androidVersion: String,
    val sdk: Int,
    val securityPatch: String,
    val patchAgeDays: Long,
    val batteryPercent: Int,
    val batteryTempC: Double,
    val batteryHealth: String,
    val storageUsedBytes: Long,
    val storageTotalBytes: Long,
    val storageUsedPct: Double,
    val networkType: String,
    val networkValidated: Boolean,
    val thermalStatus: String,
    val uptimeDays: Long,
    val rawReport: String
)

data class BatteryReading(
    val percent: Int? = null,
    val temperatureC: Double? = null,
    val health: String? = null,
    val voltageMv: Int? = null,
    val charging: String = "Unavailable",
    val technology: String? = null
)
data class StorageReading(val total: Long, val available: Long) {
    val used: Long get() = total - available
    val usedPercent: Double get() = used.toDouble() / total * 100
}
data class MemoryReading(val total: Long, val available: Long, val lowMemory: Boolean)
data class ConnectionReading(
    val connected: Boolean = false,
    val type: String = "Offline",
    val validated: Boolean = false,
    val internetCapable: Boolean = false,
    val captivePortal: Boolean = false,
    val metered: Boolean = false,
    val vpn: Boolean = false
)
data class DeviceSnapshot(
    val timestamp: Long,
    val manufacturer: String = "Unknown",
    val model: String = "Unknown",
    val androidVersion: String = "Unknown",
    val sdk: Int = 23,
    val securityPatch: String = "",
    val battery: BatteryReading = BatteryReading(),
    val storage: StorageReading? = null,
    val memory: MemoryReading? = null,
    val connection: ConnectionReading? = null,
    val thermalStatus: Int? = null,
    val sensorCount: Int? = null,
    val secureLock: Boolean? = null,
    val automaticTime: Boolean? = null,
    val powerSaver: Boolean? = null,
    val uptimeMillis: Long = 0
)
data class ScanReport(
    val scan: ScanEntity,
    val findings: List<Finding>,
    val smartSummary: String,
    val changeSummary: String,
    val rawDetails: String,
    val snapshot: DeviceSnapshot
)
data class NetworkCheck(
    val name: String,
    val success: Boolean,
    val elapsedMs: Long,
    val detail: String
)
data class NetworkDoctorResult(
    val summary: String = "Check whether two independent HTTPS endpoints are reachable.",
    val running: Boolean = false,
    val checks: List<NetworkCheck> = emptyList(),
    val timestamp: Long? = null
) {
    fun asText(): String = buildString {
        timestamp?.let { appendLine("Test time: ${formatTime(it)}") }
        appendLine(summary)
        checks.forEach { appendLine("${it.name}: ${if (it.success) "Reached" else "Not verified"} (${it.elapsedMs} ms). ${it.detail}") }
        appendLine("Endpoint reachability is not a speed test or a guarantee that every website works.")
    }.trim()
}
