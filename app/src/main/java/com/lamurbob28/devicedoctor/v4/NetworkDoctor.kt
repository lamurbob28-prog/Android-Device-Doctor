package com.lamurbob28.devicedoctor.v4

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.net.HttpURLConnection
import java.net.URL

/** Two small, explicit HTTPS requests. No background polling or bulk speed-test downloads. */
class NetworkDoctor(private val context: Context) : AutoCloseable {
    private val probe = HttpsProbe()

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
        ).map { (name, url) -> async {
            // Preserve the captured network's DNS, proxy, and route for each request.
            probe.run(name) { network.openConnection(URL(url)) as HttpURLConnection }
        } }.awaitAll()
        NetworkDoctorResult(
            summary = NetworkAssessment.summary(checks, manager?.activeNetwork != network, reading?.captivePortal == true),
            checks = checks,
            timestamp = System.currentTimeMillis()
        )
    }

    override fun close() { probe.close() }
}
