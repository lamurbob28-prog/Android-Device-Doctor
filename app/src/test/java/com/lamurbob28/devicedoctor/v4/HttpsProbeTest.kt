package com.lamurbob28.devicedoctor.v4

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

class HttpsProbeTest {
    private class FakeConnection(private val response: () -> Int) : HttpURLConnection(URL("https://example.invalid")) {
        val disconnected = AtomicBoolean(false)
        override fun connect() = Unit
        override fun usingProxy() = false
        override fun disconnect() { disconnected.set(true) }
        override fun getResponseCode(): Int = response()
    }

    @Test fun `strict response and resource cleanup occur without real network access`() = runBlocking {
        val probe = HttpsProbe()
        try {
            val connection = FakeConnection { 302 }
            assertFalse(probe.run("Test") { connection }.success)
            assertFalse(connection.instanceFollowRedirects)
            assertTrue(connection.disconnected.get())
        } finally { probe.close() }
    }

    @Test fun `timeout stops UI waiting and disconnects the request`() = runBlocking {
        val probe = HttpsProbe(timeoutMs = 500)
        val blocker = CountDownLatch(1)
        val connection = FakeConnection { blocker.await(); 204 }
        try {
            val result = withTimeout(2_000) { probe.run("Blocked") { connection } }
            assertFalse(result.success)
            assertTrue(result.detail.contains("time limit"))
            assertTrue(connection.disconnected.get())
        } finally { blocker.countDown(); probe.close() }
    }

    @Test fun `user cancellation disconnects ongoing work and permits another request`() = runBlocking {
        val probe = HttpsProbe()
        val entered = CompletableDeferred<Unit>()
        val blocker = CountDownLatch(1)
        val connection = FakeConnection { entered.complete(Unit); blocker.await(); 204 }
        try {
            val job = async { probe.run("Cancelled") { connection } }
            withTimeout(2_000) { entered.await() }
            job.cancelAndJoin()
            assertTrue(connection.disconnected.get())
            assertTrue(withTimeout(2_000) { probe.run("Retry") { FakeConnection { 204 } } }.success)
        } finally { blocker.countDown(); probe.close() }
    }
}
