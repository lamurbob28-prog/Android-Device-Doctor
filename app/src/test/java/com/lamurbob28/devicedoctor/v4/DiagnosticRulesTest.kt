package com.lamurbob28.devicedoctor.v4

import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class DiagnosticRulesTest {
    private val now = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse("2026-09-10")!!.time
    private fun device() = DeviceSnapshot(
        timestamp = now, securityPatch = "2026-08-01", battery = BatteryReading(80, 30.0, "Good"),
        storage = StorageReading(64 * DiagnosticRules.GIB, 30 * DiagnosticRules.GIB),
        memory = MemoryReading(4 * DiagnosticRules.GIB, DiagnosticRules.GIB, false),
        connection = ConnectionReading(connected = true, type = "Wi-Fi", validated = true, internetCapable = true),
        thermalStatus = 0, sensorCount = 8, secureLock = true, automaticTime = true
    )
    private fun check(s: DeviceSnapshot, starts: String) = DiagnosticRules.evaluate(s).first { it.title.startsWith(starts) }

    @Test fun `missing readings never become passed checks`() {
        val findings = DiagnosticRules.evaluate(DeviceSnapshot(timestamp = now))
        assertTrue(findings.none { it.severity == Severity.GOOD })
        assertEquals("UNKNOWN", DiagnosticRules.status(findings))
    }
    @Test fun `offline and long uptime are informational without penalties`() {
        val baseline = DiagnosticRules.evaluate(device())
        val offline = DiagnosticRules.evaluate(device().copy(connection = ConnectionReading(), uptimeMillis = 80L * 86400000))
        assertEquals(DiagnosticRules.score(baseline), DiagnosticRules.score(offline))
        assertEquals(Severity.INFO, offline.first { it.title == "You're offline" }.severity)
        assertFalse(offline.first { it.title == "Time since restart" }.needsAttention)
    }
    @Test fun `invalid partial trailing and future patch dates stay unknown`() {
        listOf("", "Unknown", "2026-02-30", "2026-8-1", "2026-08-01extra", "2026-09-11").forEach {
            assertNull("Unexpected valid date: $it", DiagnosticRules.patchAgeDays(it, now))
        }
        assertEquals(40L, DiagnosticRules.patchAgeDays("2026-08-01", now))
    }
    @Test fun `patch age does not change with local time zone`() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"))
            assertEquals(0L, DiagnosticRules.patchAgeDays("2026-09-10", now))
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))
            assertEquals(0L, DiagnosticRules.patchAgeDays("2026-09-10", now))
        } finally { TimeZone.setDefault(original) }
    }
    @Test fun `a negative battery temperature is a real cold reading`() {
        val finding = DiagnosticRules.evaluate(device().copy(battery = BatteryReading(80, -1.0, "Good"))).first { it.title.contains("freezing") }
        assertEquals(Severity.WARNING, finding.severity)
        assertNotEquals("Unavailable", temperatureText(-1.0))
    }
    @Test fun `temperature boundaries and invalid floats are handled`() {
        listOf(39.9 to Severity.GOOD, 40.0 to Severity.WARNING, 45.0 to Severity.BAD).forEach { (temperature, severity) ->
            val findings = DiagnosticRules.evaluate(device().copy(battery = BatteryReading(80, temperature, "Good")))
            assertEquals(severity, findings.first { it.title == "Battery temperature" || it.title == "Battery is warm" || it.title == "Battery is hot" }.severity)
        }
        listOf(Double.NaN, Double.POSITIVE_INFINITY, 120.0).forEach {
            assertEquals(Severity.INFO, check(device().copy(battery = BatteryReading(80, it, "Good")), "Battery temperature").severity)
        }
    }
    @Test fun `full or low absolute storage raises an actionable alert`() {
        val full = check(device().copy(storage = StorageReading(64 * DiagnosticRules.GIB, 0)), "Storage is almost full")
        assertEquals(Severity.BAD, full.severity)
        assertEquals(DoctorAction.STORAGE, full.action)
        assertEquals(Severity.BAD, check(device().copy(storage = StorageReading(4 * DiagnosticRules.GIB, DiagnosticRules.GIB / 2)), "Storage is almost full").severity)
        assertEquals(Severity.INFO, check(device().copy(storage = StorageReading(0, 0)), "Storage unavailable").severity)
    }
    @Test fun `low free RAM without Android pressure is not a false alert`() {
        val s = device().copy(memory = MemoryReading(4 * DiagnosticRules.GIB, 100_000_000, false))
        assertEquals(Severity.GOOD, check(s, "Memory pressure").severity)
        assertEquals(Severity.WARNING, check(s.copy(memory = s.memory!!.copy(lowMemory = true)), "System memory").severity)
    }
    @Test fun `sensor inventory does not certify sensors`() {
        assertEquals(Severity.INFO, check(device().copy(sensorCount = 0), "Sensor inventory").severity)
    }
    @Test fun `battery flag explanation never equates good with capacity`() {
        val finding = check(device(), "Battery condition")
        assertTrue(finding.advice.contains("does not mean 100% remaining capacity"))
    }
    @Test fun `critical findings are not hidden behind a high aggregate score`() {
        val findings = listOf(Finding(Severity.BAD, "Failure", "", "", penalty = 5))
        assertEquals(95, DiagnosticRules.score(findings))
        assertEquals("BAD", DiagnosticRules.status(findings))
    }
    @Test fun `captured reports include advice and missing values are readable`() {
        val report = DiagnosticsEngine.report(DeviceSnapshot(timestamp = now), null)
        assertEquals(-1, report.scan.score)
        assertFalse(report.rawDetails.contains("-273.15"))
        assertFalse(report.rawDetails.contains("-1%"))
        assertTrue(report.rawDetails.contains("FINDINGS"))
        assertTrue(report.rawDetails.contains(report.findings.first().advice))
    }
    @Test fun `partition change disables misleading storage delta`() {
        val report = DiagnosticsEngine.report(device(), null)
        val changed = report.scan.copy(storageTotalBytes = report.scan.storageTotalBytes * 2)
        assertTrue(DiagnosticsEngine.changes(report.scan, changed).contains("partition size changed"))
    }
    @Test fun `binary storage units are labeled accurately`() {
        assertTrue(bytes(DiagnosticRules.GIB).endsWith("GiB"))
        assertEquals("Unavailable", bytes(-1))
        assertEquals("Unavailable", batteryLevel(-1))
    }
}
