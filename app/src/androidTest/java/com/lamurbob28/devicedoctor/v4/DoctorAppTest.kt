package com.lamurbob28.devicedoctor.v4

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DoctorAppTest {
    @get:Rule val compose = createAndroidComposeRule<V4MainActivity>()
    private lateinit var vm: DoctorViewModel

    @Before fun awaitScan() {
        compose.runOnIdle { vm = ViewModelProvider(compose.activity)[DoctorViewModel::class.java] }
        compose.waitUntil(15_000) { !vm.isScanning && vm.latestReport != null }
    }

    @Test fun navigationManualTestsAndReportSurviveRecreation() {
        compose.onNodeWithTag("scan-button").assertIsEnabled()
        screenshot("overview")
        compose.onNodeWithTag("tab-1").performClick()
        compose.onNodeWithText("Know what needs attention").assertIsDisplayed()
        screenshot("checks")
        compose.onNodeWithTag("tab-2").performClick()
        compose.onNodeWithTag("run-network").assertIsDisplayed()
        screenshot("tools")
        compose.onNodeWithTag("tools-list").performScrollToNode(hasTestTag("tool-Touchscreen"))
        compose.onNodeWithTag("tool-Touchscreen").performClick()
        compose.onNodeWithTag("touch-grid").performTouchInput { swipe(Offset(10f, 10f), Offset(width - 10f, height - 10f), 600) }
        compose.onNodeWithTag("touch-count").assertTextContains("/60 cells", substring = true)
        compose.onNodeWithTag("touch-count").assert(hasText("0/60 cells", substring = true).not())
        screenshot("touchscreen")
        compose.onNodeWithTag("close-hardware").performClick()
        compose.onNodeWithTag("tools-list").performScrollToNode(hasTestTag("tool-Display"))
        compose.onNodeWithTag("tool-Display").performClick()
        compose.onNodeWithText("1/5 · Red").assertIsDisplayed()
        compose.onNodeWithText("Next color").performClick()
        compose.onNodeWithText("2/5 · Green").assertIsDisplayed()
        compose.onNodeWithTag("close-hardware").performClick()
        compose.onNodeWithTag("tab-3").performClick()
        screenshot("history")
        val timestamp = vm.latestReport!!.scan.timestamp
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("Your device over time").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(timestamp, ViewModelProvider(compose.activity)[DoctorViewModel::class.java].latestReport!!.scan.timestamp)
        }
    }

    @Test fun repeatedScanRequestsDoNotInsertDuplicatesAndClearStaysEmpty() {
        val dao = AppDatabase.get(compose.activity).scanDao()
        val before = runBlocking { dao.observeRecentScans().first().size }
        compose.runOnIdle { repeat(10) { vm.refreshScan() } }
        compose.waitUntil(15_000) { !vm.isScanning }
        assertEquals((before + 1).coerceAtMost(50), runBlocking { dao.observeRecentScans().first().size })
        compose.onNodeWithTag("tab-3").performClick()
        compose.onNodeWithTag("history-list").performScrollToNode(hasTestTag("clear-history"))
        compose.onNodeWithTag("clear-history").performClick()
        compose.onNodeWithText("Keep scans").performClick()
        assertTrue(runBlocking { dao.observeRecentScans().first().isNotEmpty() })
        compose.onNodeWithTag("clear-history").performClick()
        compose.onNodeWithTag("confirm-clear").performClick()
        compose.waitUntil(10_000) { !vm.isClearing && vm.latestReport == null }
        compose.onNodeWithTag("empty-history").assertIsDisplayed()
        assertNull(runBlocking { dao.latestScan() })
        compose.onNodeWithTag("tab-0").performClick()
        compose.onNodeWithText("Ready for a checkup").assertIsDisplayed()
        assertNull(runBlocking { dao.latestScan() })
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val width = minOf(720, bitmap.width)
        val scaled = Bitmap.createScaledBitmap(bitmap, width, bitmap.height * width / bitmap.width, true)
        val directory = File(compose.activity.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
    }
}
