package com.lamurbob28.devicedoctor.v4

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {
    @Query("SELECT * FROM scans ORDER BY timestamp DESC, id DESC LIMIT 50")
    fun observeRecentScans(): Flow<List<ScanEntity>>

    @Query("SELECT * FROM scans ORDER BY timestamp DESC, id DESC LIMIT 1")
    suspend fun latestScan(): ScanEntity?

    @Insert
    suspend fun insert(scan: ScanEntity)

    @Query("DELETE FROM scans WHERE id NOT IN (SELECT id FROM scans ORDER BY timestamp DESC, id DESC LIMIT 50)")
    suspend fun trimHistory()

    @Transaction
    suspend fun save(scan: ScanEntity) {
        insert(scan)
        trimHistory()
    }

    @Query("DELETE FROM scans")
    suspend fun clearHistory()
}
