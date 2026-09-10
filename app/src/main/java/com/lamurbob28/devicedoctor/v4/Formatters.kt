package com.lamurbob28.devicedoctor.v4

import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

fun formatTime(ms: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))
fun oneDecimal(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)
fun yesNo(value: Boolean): String = if (value) "Yes" else "No"
fun bytes(value: Long): String {
    if (value < 0) return "Unavailable"
    val gib = value / 1073741824.0
    return when {
        gib >= 1 -> "${oneDecimal(gib)} GiB"
        value >= 1048576 -> "${oneDecimal(value / 1048576.0)} MiB"
        value >= 1024 -> "${oneDecimal(value / 1024.0)} KiB"
        else -> "$value B"
    }
}
fun signedBytes(value: Long): String = if (value < 0) "−${bytes(abs(value))}" else "+${bytes(value)}"
fun temperatureText(value: Double?): String = value?.takeIf { it.isFinite() && it in -50.0..100.0 }?.let { "${oneDecimal(it)} °C" } ?: "Unavailable"
fun batteryLevel(value: Int?): String = value?.takeIf { it in 0..100 }?.let { "$it%" } ?: "Unavailable"
fun duration(ms: Long): String {
    val minutes = ms.coerceAtLeast(0) / 60_000
    return "${minutes / 1440}d ${(minutes % 1440) / 60}h ${minutes % 60}m"
}
fun thermalName(value: Int?): String = when (value) {
    0 -> "None"; 1 -> "Light"; 2 -> "Moderate"; 3 -> "Severe"; 4 -> "Critical"
    5 -> "Emergency"; 6 -> "Shutdown"; else -> "Unavailable"
}
fun statusLabel(value: String): String = when (value) {
    "BAD" -> "Needs attention"; "WARNING" -> "Worth checking"; "GOOD" -> "No alerts found"; else -> "Incomplete scan"
}
