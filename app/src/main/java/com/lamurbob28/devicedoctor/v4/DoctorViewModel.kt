package com.lamurbob28.devicedoctor.v4

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DoctorViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.get(application).scanDao()
    private val diagnostics = DiagnosticsEngine(application)
    private val networkDoctor = NetworkDoctor(application)

    val history: StateFlow<List<ScanEntity>> = dao.observeRecentScans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var latestReport by mutableStateOf<ScanReport?>(null)
        private set

    var networkResult by mutableStateOf(NetworkDoctorResult())
        private set

    init {
        refreshScan()
    }

    fun refreshScan() {
        viewModelScope.launch {
            val previous = dao.latestScan()
            val report = diagnostics.scan(previous)
            dao.insert(report.scan)
            dao.trimHistory()
            latestReport = report
        }
    }

    fun runNetworkDoctor() {
        networkResult = NetworkDoctorResult("Running network tests...", true)
        viewModelScope.launch {
            val text = networkDoctor.run()
            networkResult = NetworkDoctorResult(text, false)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            dao.clearHistory()
            latestReport = null
            refreshScan()
        }
    }

    fun fullReport(): String {
        val report = latestReport ?: return "Device Doctor v4.0: no scan report yet."
        return "Device Doctor v4.0 Smart Report\n\nSMART SUMMARY\n" + report.smartSummary +
                "\nWHAT CHANGED\n" + report.changeSummary +
                "\nRAW DETAILS\n" + report.rawDetails +
                "\nNETWORK DOCTOR\n" + networkResult.text
    }
}
