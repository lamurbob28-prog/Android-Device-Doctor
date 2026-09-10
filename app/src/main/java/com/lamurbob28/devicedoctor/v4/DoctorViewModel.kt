package com.lamurbob28.devicedoctor.v4

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class DoctorViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.get(application).scanDao()
    private val diagnostics = DiagnosticsEngine(application)
    private val networkDoctor = NetworkDoctor(application)
    private val historyMutex = Mutex()
    private var scanJob: Job? = null
    private var networkJob: Job? = null

    var message by mutableStateOf<String?>(null)
        private set
    val history: StateFlow<List<ScanEntity>> = dao.observeRecentScans()
        .catch { message = "Saved history could not be loaded. Your existing scans have not been reset." }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    var latestReport by mutableStateOf<ScanReport?>(null)
        private set
    var isScanning by mutableStateOf(false)
        private set
    var isClearing by mutableStateOf(false)
        private set
    var networkResult by mutableStateOf(NetworkDoctorResult())
        private set

    init { refreshScan() }

    fun dismissMessage() { message = null }

    fun refreshScan() {
        if (isScanning || isClearing) return
        isScanning = true
        scanJob = viewModelScope.launch {
            try {
                historyMutex.withLock {
                    var historyReadable = true
                    val previous = try { dao.latestScan() } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        historyReadable = false
                        message = "This scan will work, but saved history could not be read. Existing data has not been reset."
                        null
                    }
                    val report = withContext(Dispatchers.IO) { diagnostics.scan(previous) }
                    ensureActive()
                    latestReport = report
                    if (historyReadable) {
                        try { dao.save(report.scan) } catch (error: Exception) {
                            if (error is CancellationException) throw error
                            message = "The scan finished, but could not be saved. You can still share or save this report."
                        }
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                message = "The scan could not finish. Try Scan again; the previous report is still available."
            } finally { isScanning = false }
        }
    }

    fun runNetworkDoctor() {
        if (networkResult.running) return
        networkResult = NetworkDoctorResult("Testing two HTTPS endpoints…", running = true)
        networkJob = viewModelScope.launch {
            try { networkResult = networkDoctor.run() }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                networkResult = NetworkDoctorResult("The test could not finish. Check the connection and try again.")
            }
        }
    }

    fun cancelNetworkDoctor() {
        networkJob?.cancel()
        networkResult = NetworkDoctorResult("Test cancelled. You can run it again whenever you're ready.")
    }

    fun clearHistory() {
        if (isClearing) return
        isClearing = true
        scanJob?.cancel()
        viewModelScope.launch {
            try {
                historyMutex.withLock {
                    dao.clearHistory()
                    latestReport = null
                    message = "History cleared. Tap Scan device when you want a new baseline."
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                message = "History could not be cleared. Try again."
            } finally { isClearing = false }
        }
    }

    fun fullReport(): String? = latestReport?.let {
        "${it.rawDetails}\n\nCHANGES\n${it.changeSummary}\n\nCONNECTION TEST\n${networkResult.asText()}"
    }

    fun saveReport(uri: Uri, text: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val stream = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                        ?: error("No output stream")
                    stream.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
                }
                message = "Report saved."
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                message = "The report could not be saved. Check the selected destination and try again."
            }
        }
    }

    override fun onCleared() {
        networkJob?.cancel()
        networkDoctor.close()
        super.onCleared()
    }
}
