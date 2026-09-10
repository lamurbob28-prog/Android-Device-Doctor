package com.lamurbob28.devicedoctor.v4

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScanDatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun sample(timestamp: Long = 1234L) = ScanEntity(
        timestamp = timestamp, score = 85, status = "WARNING", androidVersion = "15", sdk = 35,
        securityPatch = "2026-08-01", patchAgeDays = 40, batteryPercent = 60, batteryTempC = 30.0,
        batteryHealth = "Good", storageUsedBytes = 100, storageTotalBytes = 1000, storageUsedPct = 10.0,
        networkType = "Wi-Fi", networkValidated = true, thermalStatus = "None", uptimeDays = 2,
        rawReport = "Original saved v4 report"
    )

    @Test fun savesAreAtomicAndKeepFiftyWithDeterministicTies() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = database.scanDao()
            repeat(65) { dao.save(sample().copy(rawReport = "scan $it")) }
            val scans = dao.observeRecentScans().first()
            assertEquals(50, scans.size)
            assertEquals("scan 64", dao.latestScan()!!.rawReport)
            assertEquals("scan 15", scans.last().rawReport)
            dao.clearHistory()
            assertNull(dao.latestScan())
            assertTrue(dao.observeRecentScans().first().isEmpty())
        } finally { database.close() }
    }

    @Test fun originalV4SchemaAndSavedDataRemainReadable() = runBlocking {
        val name = "doctor-v4-compatibility-test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        val old = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            // The original v4 table schema, created independently from current Room generated code.
            old.execSQL("""CREATE TABLE IF NOT EXISTS scans (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                timestamp INTEGER NOT NULL, score INTEGER NOT NULL, status TEXT NOT NULL,
                androidVersion TEXT NOT NULL, sdk INTEGER NOT NULL, securityPatch TEXT NOT NULL,
                patchAgeDays INTEGER NOT NULL, batteryPercent INTEGER NOT NULL, batteryTempC REAL NOT NULL,
                batteryHealth TEXT NOT NULL, storageUsedBytes INTEGER NOT NULL, storageTotalBytes INTEGER NOT NULL,
                storageUsedPct REAL NOT NULL, networkType TEXT NOT NULL, networkValidated INTEGER NOT NULL,
                thermalStatus TEXT NOT NULL, uptimeDays INTEGER NOT NULL, rawReport TEXT NOT NULL)""")
            old.execSQL("""INSERT INTO scans VALUES
                (1, 1234, 85, 'WARNING', '15', 35, '2026-08-01', 40, 60, 30.0, 'Good',
                 100, 1000, 10.0, 'Wi-Fi', 1, 'None', 2, 'Original saved v4 report')""")
            old.version = 1
        } finally { old.close() }
        val upgraded = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            val scan = upgraded.scanDao().latestScan()
            assertEquals("Original saved v4 report", scan!!.rawReport)
            upgraded.scanDao().save(sample(2345))
            assertEquals(2, upgraded.scanDao().observeRecentScans().first().size)
        } finally { upgraded.close(); context.deleteDatabase(name) }
    }
}
