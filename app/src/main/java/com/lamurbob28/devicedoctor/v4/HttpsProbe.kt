package com.lamurbob28.devicedoctor.v4

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLException
import kotlin.coroutines.resume

/** Bound both waiting time and queued work even when a legacy resolver ignores interruption. */
internal class HttpsProbe(private val timeoutMs: Long = 8_000) : AutoCloseable {
    private val executor = ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue<Runnable>(2),
        { runnable -> Thread(runnable, "doctor-network").apply { isDaemon = true } }, ThreadPoolExecutor.AbortPolicy())
    private fun elapsed() = System.nanoTime() / 1_000_000

    suspend fun run(name: String, open: () -> HttpURLConnection): NetworkCheck {
        val start = elapsed()
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<NetworkCheck> { continuation ->
                val connection = AtomicReference<HttpURLConnection?>(null)
                try {
                    val future = executor.submit {
                        if (!continuation.isActive) return@submit
                        val result = try {
                            val http = open()
                            connection.set(http)
                            if (!continuation.isActive) return@submit
                            http.connectTimeout = 4_000
                            http.readTimeout = 4_000
                            http.instanceFollowRedirects = false
                            http.useCaches = false
                            http.setRequestProperty("User-Agent", "DeviceDoctor/5.0")
                            val code = http.responseCode
                            NetworkCheck(name, NetworkAssessment.isExpectedResponse(code), elapsed() - start, NetworkAssessment.httpDetail(code))
                        } catch (error: Exception) {
                            val detail = when (error) {
                                is UnknownHostException -> "DNS could not resolve this endpoint. Filtering or a DNS problem may be involved."
                                is SSLException -> "A secure TLS connection could not be verified. Check the clock and network."
                                is SocketTimeoutException -> "The endpoint timed out. That alone does not prove the whole connection is down."
                                else -> "This endpoint could not be reached (${error.javaClass.simpleName})."
                            }
                            NetworkCheck(name, false, elapsed() - start, detail)
                        } finally {
                            connection.getAndSet(null)?.let { runCatching { it.disconnect() } }
                        }
                        if (continuation.isActive) continuation.resume(result)
                    }
                    continuation.invokeOnCancellation {
                        connection.getAndSet(null)?.let { runCatching { it.disconnect() } }
                        future.cancel(true)
                        // Cancelled queued futures must not build up behind stuck DNS calls.
                        executor.remove(future as Runnable)
                    }
                } catch (_: RejectedExecutionException) {
                    if (continuation.isActive) continuation.resume(NetworkCheck(name, false, elapsed() - start,
                        "Earlier network work is still stopping. Wait briefly and try again."))
                }
            }
        } ?: NetworkCheck(name, false, elapsed() - start, "Stopped after the time limit. A blocked or slow endpoint is inconclusive.")
    }

    override fun close() { executor.shutdownNow() }
}
