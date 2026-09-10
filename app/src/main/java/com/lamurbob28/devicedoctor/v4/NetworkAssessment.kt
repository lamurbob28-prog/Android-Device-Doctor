package com.lamurbob28.devicedoctor.v4

object NetworkAssessment {
    fun isExpectedResponse(code: Int): Boolean = code == 204
    fun httpDetail(code: Int): String = when {
        code == 204 -> "Expected HTTP 204 received after DNS, connection, and TLS."
        code in 300..399 -> "HTTP $code redirect instead of the expected 204. A sign-in page or filtering may be involved."
        else -> "HTTP $code instead of the expected 204. Internet access to this endpoint is not verified."
    }
    fun summary(checks: List<NetworkCheck>, changed: Boolean, captivePortal: Boolean): String = when {
        changed -> "The active network changed during testing. Results refer to the earlier connection; test again."
        checks.isEmpty() -> "No endpoint results are available."
        checks.all { it.success } -> "Both endpoints are reachable over HTTPS. This does not measure download speed or prove every website works."
        captivePortal -> "Android reports a network sign-in page. Complete sign-in in connection settings, then test again."
        checks.any { it.success } -> "One endpoint is reachable. The other may be blocked or unavailable; the whole connection is not necessarily broken."
        else -> "Neither endpoint was verified. Check connection settings, network sign-in, the clock, or filtering, then retry."
    }
}
