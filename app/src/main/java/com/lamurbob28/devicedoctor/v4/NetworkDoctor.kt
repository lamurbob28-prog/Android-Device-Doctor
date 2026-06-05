package com.lamurbob28.devicedoctor.v4

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

class NetworkDoctor(private val context: Context) {
    suspend fun run(): String = withContext(Dispatchers.IO) {
        val androidNetwork = readAndroidNetwork()
        val dnsGoogle = timedDns("google.com")
        val dnsCloudflare = timedDns("cloudflare.com")
        val tcpCloudflare = timedTcp("1.1.1.1", 443, 3000)
        val tcpGoogle = timedTcp("8.8.8.8", 443, 3000)
        val http = timedHttp("https://www.google.com/generate_204", 5000)

        var problems = 0
        if (!androidNetwork.hasNetwork || !androidNetwork.hasInternet) problems++
        if (!dnsGoogle.success && !dnsCloudflare.success) problems++
        if (!tcpCloudflare.success && !tcpGoogle.success) problems++
        if (!http.success) problems++
        if (tcpCloudflare.success && tcpCloudflare.ms > 800) problems++

        buildString {
            appendLine("Network Doctor v4.0")
            appendLine("Android network type: ${androidNetwork.type}")
            appendLine("Android validated: ${yesNo(androidNetwork.validated)}")
            appendLine("Android internet capability: ${yesNo(androidNetwork.hasInternet)}")
            appendLine()
            appendLine("TESTS")
            appendLine(format("DNS google.com", dnsGoogle))
            appendLine(format("DNS cloudflare.com", dnsCloudflare))
            appendLine(format("TCP 1.1.1.1:443", tcpCloudflare))
            appendLine(format("TCP 8.8.8.8:443", tcpGoogle))
            append("HTTPS generate_204: ${if (http.success) "OK" else "FAILED"} in ${http.ms} ms (HTTP ${http.code})")
            if (http.error != null) append(" - ${http.error}")
            appendLine()
            appendLine()
            appendLine("RESULT")
            when {
                problems == 0 -> appendLine("Status: GOOD\nInternet looks reachable. Latency and DNS are healthy.")
                problems <= 2 -> appendLine("Status: WARNING\nSome checks failed or looked slow. Try toggling Wi-Fi, switching networks, or restarting the router.")
                else -> appendLine("Status: BAD\nMultiple network checks failed. This connection may be broken, captive, or blocked.")
            }
        }
    }

    private fun readAndroidNetwork(): AndroidNetwork {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return AndroidNetwork()
        val network = cm.activeNetwork ?: return AndroidNetwork()
        val caps = cm.getNetworkCapabilities(network) ?: return AndroidNetwork(hasNetwork = true)
        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Other"
        }
        return AndroidNetwork(
            hasNetwork = true,
            hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            type = type
        )
    }

    private fun timedDns(host: String): TimedResult {
        val start = System.currentTimeMillis()
        return try {
            val addresses = InetAddress.getAllByName(host)
            TimedResult(true, System.currentTimeMillis() - start, addresses.firstOrNull()?.hostAddress ?: "No addresses")
        } catch (e: Exception) {
            TimedResult(false, System.currentTimeMillis() - start, simpleError(e))
        }
    }

    private fun timedTcp(host: String, port: Int, timeoutMs: Int): TimedResult {
        val start = System.currentTimeMillis()
        val socket = Socket()
        return try {
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            TimedResult(true, System.currentTimeMillis() - start, "Connected")
        } catch (e: Exception) {
            TimedResult(false, System.currentTimeMillis() - start, simpleError(e))
        } finally {
            try { socket.close() } catch (_: Exception) { }
        }
    }

    private fun timedHttp(urlText: String, timeoutMs: Int): HttpResult {
        val start = System.currentTimeMillis()
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(urlText).openConnection() as HttpURLConnection
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.useCaches = false
            connection.instanceFollowRedirects = false
            connection.connect()
            val code = connection.responseCode
            HttpResult(code in 200..399, System.currentTimeMillis() - start, code, null)
        } catch (e: Exception) {
            HttpResult(false, System.currentTimeMillis() - start, -1, simpleError(e))
        } finally {
            connection?.disconnect()
        }
    }

    private fun format(label: String, result: TimedResult): String = "$label: ${if (result.success) "OK" else "FAILED"} in ${result.ms} ms - ${result.message}"
    private fun yesNo(value: Boolean): String = if (value) "Yes" else "No"
    private fun simpleError(e: Exception): String = e::class.java.simpleName + (e.message?.let { ": $it" } ?: "")

    private data class AndroidNetwork(val hasNetwork: Boolean = false, val hasInternet: Boolean = false, val validated: Boolean = false, val type: String = "None")
    private data class TimedResult(val success: Boolean, val ms: Long, val message: String)
    private data class HttpResult(val success: Boolean, val ms: Long, val code: Int, val error: String?)
}
