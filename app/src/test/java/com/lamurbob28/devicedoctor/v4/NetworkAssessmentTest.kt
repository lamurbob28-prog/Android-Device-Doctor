package com.lamurbob28.devicedoctor.v4

import org.junit.Assert.*
import org.junit.Test

class NetworkAssessmentTest {
    @Test fun `redirects and unexpected success pages do not count as expected responses`() {
        assertTrue(NetworkAssessment.isExpectedResponse(204))
        listOf(200, 201, 301, 302, 307, 401, 500, -1).forEach { assertFalse(NetworkAssessment.isExpectedResponse(it)) }
    }
    @Test fun `one blocked endpoint does not imply a total outage`() {
        val checks = listOf(NetworkCheck("A", true, 50, "204"), NetworkCheck("B", false, 8000, "Timed out"))
        assertTrue(NetworkAssessment.summary(checks, false, false).contains("not necessarily broken"))
    }
    @Test fun `network switches invalidate a broad conclusion even when requests pass`() {
        val checks = listOf(NetworkCheck("A", true, 50, "204"), NetworkCheck("B", true, 60, "204"))
        assertTrue(NetworkAssessment.summary(checks, true, false).startsWith("The active network changed"))
    }
    @Test fun `empty results cannot pass and captive portals explain next action`() {
        assertEquals("No endpoint results are available.", NetworkAssessment.summary(emptyList(), false, false))
        assertTrue(NetworkAssessment.summary(listOf(NetworkCheck("A", false, 2, "302")), false, true).contains("sign-in"))
    }
}
