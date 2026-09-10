package com.lamurbob28.devicedoctor.v4

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLException
import kotlin.coroutines.resume

/** Two small, explicit HTTPS requests. No background polling or bulk speed-test downloads. */
class NetworkDoctor(private val context: Context) : AutoCloseable {
    // A resolver on older Android can ignore interrupts. Bound both worker count and UI wait time.
    private val executor = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "doctor-network").apply { isDaemon = true }
    }

    suspend fun run(): NetworkDoctorResult = coroutineScope {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = manager?.activeNetwork
        val reading = readConnection(context)
        if (network == null) return@coroutineScope NetworkDoctorResult(
            summary = "No active connection to test. Connect to Wi-Fi or mobile data, then try again.",
            timestamp = System.currentTimeMillis()
        )
        val checks = listOf(
            "Google" to "https://www.google.com/generate_204",
            "Cloudflare" to "https://cp.cloudflare.com/generate_204"
        ).map { (name, url) -> async { probe(network, name, url) } }.awaitAll()
        val changed = manager.activeNetwork != network
        NetworkDoctorResult(
            summary = NetworkAssessment.summary(checks, changed, reading?.captivePortal == true),
            checks = checks,
            timestamp = System.currentTimeMillis()
        )
    }

    private suspend fun probe(network: Network, name: String, url: String): NetworkCheck {
        val start = SystemClock.elapsedRealtime()
        return withTimeoutOrNull(8_000) {
            suspendCancellableCoroutine { continuation ->
                val connection = AtomicReference<HttpURLConnection?>(null)
                val future = executor.submit {
                    if (!continuation.isActive) return@submit
                    val result = try {
                        // Keep each request on the network captured at the start, including its DNS/proxy.
                        val http = network.openConnection(URL(url)) as HttpURLConnection
                        connection.set(http)
                        if (!continuation.isActive) {
                            http.disconnect()
                            return@submit
                        }
                        http.connectTimeout = 4_000
                        http.readTimeout = 4_000
                        http.instanceFollowRedirects = false
                        http.useCaches = false
                        http.setRequestProperty("User-Agent", "DeviceDoctor/5.0")
                        val code = http.responseCode
                        NetworkCheck(name, NetworkAssessment.isExpectedResponse(code),
                            SystemClock.elapsedRealtime() - start, NetworkAssessment.httpDetail(code))
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        val detail = when (error) {
                            is UnknownHostException -> "DNS could not resolve this endpoint. Filtering or a DNS problem may be involved."
                            is SSLException -> "A secure TLS connection could not be verified. Check the clock and network."
                            is SocketTimeoutException -> "The endpoint timed out. That alone does not prove the whole connection is down."
                            else -> "This endpoint could not be reached (${error.javaClass.simpleName})."
                        }
                        NetworkCheck(name, false, SystemClock.elapsedRealtime() - start, detail)
                    } finally {
                        connection.getAndSet(null)?.disconnect()
                    }
                    if (continuation.isActive) continuation.resume(result)
                }
                continuation.invokeOnCancellation {
                    future.cancel(true)
                    connection.getAndSet(null)?.disconnect()
                }
            }
        } ?: NetworkCheck(name, false, SystemClock.elapsedRealtime() - start,
            "Stopped after the 8-second limit. A blocked or slow endpoint is inconclusive.")
    }

    override fun close() { executor.shutdownNow() }
}
