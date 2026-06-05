package com.lamurbob28.devicedoctor.v4

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class Severity { GOOD, WARNING, BAD, INFO }

data class Finding(
    val severity: Severity,
    val title: String,
    val detail: String,
    val advice: String
)

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

data class ScanReport(
    val scan: ScanEntity,
    val findings: List<Finding>,
    val smartSummary: String,
    val changeSummary: String,
    val rawDetails: String
)

data class NetworkDoctorResult(
    val text: String = "Network Doctor has not been run yet.",
    val running: Boolean = false
)
