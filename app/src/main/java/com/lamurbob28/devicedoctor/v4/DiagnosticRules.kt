package com.lamurbob28.devicedoctor.v4

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Pure, deterministic rules. Missing measurements are never promoted to passing checks. */
object DiagnosticRules {
    const val GIB = 1024L * 1024 * 1024

    fun patchAgeDays(patch: String, now: Long): Long? {
        if (!Regex("\\d{4}-\\d{2}-\\d{2}").matches(patch)) return null
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val position = ParsePosition(0)
        val parsed = parser.parse(patch, position) ?: return null
        if (position.index != patch.length || parsed.time > now) return null
        return (now - parsed.time) / 86_400_000L
    }

    fun evaluate(s: DeviceSnapshot): List<Finding> = buildList {
        fun check(severity: Severity, title: String, detail: String, advice: String,
                  category: Category, action: DoctorAction? = null, penalty: Int = 0) {
            add(Finding(severity, title, detail, advice, category, action, penalty))
        }
        val age = patchAgeDays(s.securityPatch, s.timestamp)
        when {
            age == null -> check(Severity.INFO, "Security patch", "A valid patch age could not be established.",
                "Check the reported patch and your phone's date. A future or invalid date is not counted as current.", Category.SYSTEM, DoctorAction.UPDATE)
            age > 365 -> check(Severity.BAD, "Security patch is over a year old", "Reported patch: ${s.securityPatch} ($age days ago).",
                "Check for a system update. Patch age alone cannot tell whether an update is available for this model.", Category.SYSTEM, DoctorAction.UPDATE, 20)
            age > 180 -> check(Severity.WARNING, "Review system updates", "Reported patch: ${s.securityPatch} ($age days ago).",
                "Check the manufacturer's system updater for newer security fixes.", Category.SYSTEM, DoctorAction.UPDATE, 10)
            else -> check(Severity.GOOD, "Security patch recorded", "Reported patch: ${s.securityPatch} ($age days ago).",
                "This checks the reported date, not whether all vulnerabilities are fixed or every app is updated.", Category.SYSTEM, DoctorAction.UPDATE)
        }
        val temperature = s.battery.temperatureC?.takeIf { it.isFinite() && it in -50.0..100.0 }
        when {
            temperature == null -> check(Severity.INFO, "Battery temperature unavailable", "Android did not provide a usable temperature.",
                "An unavailable reading is not a passing temperature test.", Category.BATTERY)
            temperature >= 45 -> check(Severity.BAD, "Battery is hot", "Reported temperature: ${temperatureText(temperature)}.",
                "Pause heavy use and charging and let the phone cool naturally. If heat persists, follow the manufacturer's guidance.", Category.BATTERY, DoctorAction.BATTERY, 20)
            temperature >= 40 -> check(Severity.WARNING, "Battery is warm", "Reported temperature: ${temperatureText(temperature)}.",
                "Reduce heavy use and keep the phone out of direct sunlight. Temperature thresholds here are general guidance.", Category.BATTERY, DoctorAction.BATTERY, 10)
            temperature < 0 -> check(Severity.WARNING, "Battery is below freezing", "Reported temperature: ${temperatureText(temperature)}.",
                "Let the device return to its manufacturer's charging temperature range naturally.", Category.BATTERY, DoctorAction.BATTERY, 10)
            else -> check(Severity.GOOD, "Battery temperature", "Reported temperature: ${temperatureText(temperature)}.",
                "No temperature alert at this moment. This is battery temperature, not CPU temperature.", Category.BATTERY)
        }
        when (s.battery.health) {
            null, "Unknown" -> check(Severity.INFO, "Battery condition unavailable", "Android did not report a battery condition.",
                "Remaining capacity and battery wear cannot be measured by this check.", Category.BATTERY)
            "Good" -> check(Severity.GOOD, "Battery condition", "Android reports its condition as Good.",
                "Good is an Android status flag. It does not mean 100% remaining capacity or no battery wear.", Category.BATTERY)
            else -> check(Severity.BAD, "Battery condition needs attention", "Android reports: ${s.battery.health}.",
                "Review charging or shutdown problems with the device manufacturer if this status persists.", Category.BATTERY, DoctorAction.BATTERY, 20)
        }
        val storage = s.storage?.takeIf { it.total > 0 && it.available in 0..it.total }
        when {
            storage == null -> check(Severity.INFO, "Storage unavailable", "The data partition could not be measured.",
                "Open Android storage settings for its own breakdown.", Category.STORAGE, DoctorAction.STORAGE)
            storage.available < GIB || storage.usedPercent >= 95 -> check(Severity.BAD, "Storage is almost full", "${bytes(storage.available)} available · ${oneDecimal(storage.usedPercent)}% used.",
                "Review downloads and unused apps. Very little free space can interfere with updates and saving files.", Category.STORAGE, DoctorAction.STORAGE, 25)
            storage.available < 3 * GIB || storage.usedPercent >= 85 -> check(Severity.WARNING, "Make some storage room", "${bytes(storage.available)} available · ${oneDecimal(storage.usedPercent)}% used.",
                "Review large videos, downloads, offline media, and apps you no longer use.", Category.STORAGE, DoctorAction.STORAGE, 10)
            else -> check(Severity.GOOD, "Storage headroom", "${bytes(storage.available)} available on the data partition.",
                "Android's storage categories can differ from this filesystem measurement.", Category.STORAGE, DoctorAction.STORAGE)
        }
        val network = s.connection
        when {
            network == null -> check(Severity.INFO, "Connection status unavailable", "Android's network status could not be read.",
                "Open connection settings to inspect it.", Category.NETWORK, DoctorAction.NETWORK)
            !network.connected -> check(Severity.INFO, "You're offline", "No active network is reported.",
                "This is normal in airplane mode or with data disabled. Connect when you need internet.", Category.NETWORK, DoctorAction.NETWORK)
            network.captivePortal -> check(Severity.WARNING, "Network sign-in required", "Android reports a captive portal on ${network.type}.",
                "Complete the network's sign-in from Wi-Fi settings, then run the connection test.", Category.NETWORK, DoctorAction.NETWORK)
            !network.validated -> check(Severity.WARNING, "Internet not yet verified", "Connected via ${network.type}, but Android has not validated internet access.",
                "This may be a local-only network, filtering, or temporary loss of service. Try the connection test.", Category.NETWORK, DoctorAction.NETWORK)
            else -> check(Severity.GOOD, "Internet validated", "Android reports internet access via ${network.type}.",
                "Individual websites may still be blocked or unavailable.", Category.NETWORK)
        }
        when {
            s.memory == null -> check(Severity.INFO, "Memory status unavailable", "System memory could not be read.",
                "No RAM conclusion was made.", Category.SYSTEM)
            s.memory.lowMemory -> check(Severity.WARNING, "System memory is under pressure", "Android raised its low-memory flag; ${bytes(s.memory.available)} available.",
                "Save your work and close an unused heavy app if the phone is sluggish. Android manages RAM automatically.", Category.SYSTEM, penalty = 10)
            else -> check(Severity.GOOD, "Memory pressure", "${bytes(s.memory.available)} available of ${bytes(s.memory.total)}.",
                "Android has not raised its low-memory flag. Used RAM includes useful app caches.", Category.SYSTEM)
        }
        val thermal = s.thermalStatus
        when {
            thermal == null -> check(Severity.INFO, "Thermal status unavailable", "This device or Android version did not expose thermal status.",
                "Battery temperature is shown separately.", Category.BATTERY)
            thermal >= 3 -> check(Severity.BAD, "Thermal throttling reported", "System status: ${thermalName(thermal)}.",
                "Pause heavy workloads and allow the phone to cool. This is an Android thermal signal.", Category.BATTERY, DoctorAction.BATTERY, 20)
            thermal > 0 -> check(Severity.WARNING, "Thermal load detected", "System status: ${thermalName(thermal)}.",
                "Reduce demanding tasks if performance is dropping.", Category.BATTERY, DoctorAction.BATTERY, 5)
            else -> check(Severity.GOOD, "System thermals", "Android reports no thermal throttling.",
                "This describes the current thermal state, not a stress test.", Category.BATTERY)
        }
        when (s.secureLock) {
            false -> check(Severity.WARNING, "No secure screen lock", "Android reports no PIN, password, or pattern protecting this profile.",
                "Set a screen lock to help protect your data if the phone is lost.", Category.SYSTEM, DoctorAction.SECURITY, 10)
            true -> check(Severity.GOOD, "Screen lock enabled", "This profile has a secure screen lock.",
                "Keep your unlock method private.", Category.SYSTEM)
            null -> check(Severity.INFO, "Screen lock unavailable", "Screen-lock status could not be read.",
                "Review Android's security settings.", Category.SYSTEM, DoctorAction.SECURITY)
        }
        if (s.automaticTime == false) check(Severity.INFO, "Automatic time is off", "The clock may be set manually.",
            "An incorrect date can affect secure connections and patch-age estimates.", Category.SYSTEM, DoctorAction.DATE)
        check(Severity.INFO, "Sensor inventory", s.sensorCount?.let { "$it sensors are listed by Android." } ?: "Sensor inventory is unavailable.",
            "An inventory is not a functional test. Try the live motion and light readings in Tools.", Category.SYSTEM)
        check(Severity.INFO, "Time since restart", duration(s.uptimeMillis),
            "Long uptime alone is not a fault. Restart only when troubleshooting or completing an update.", Category.SYSTEM)
    }

    fun score(findings: List<Finding>): Int = (100 - findings.sumOf { it.penalty }).coerceIn(0, 100)
    fun status(findings: List<Finding>): String = when {
        findings.none { it.severity != Severity.INFO } -> "UNKNOWN"
        findings.any { it.severity == Severity.BAD } -> "BAD"
        findings.any { it.severity == Severity.WARNING } -> "WARNING"
        else -> "GOOD"
    }
}
