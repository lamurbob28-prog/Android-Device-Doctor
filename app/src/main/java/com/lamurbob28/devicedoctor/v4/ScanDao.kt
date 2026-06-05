package com.lamurbob28.devicedoctor.v4

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {
    @Query("SELECT * FROM scans ORDER BY timestamp DESC LIMIT 10")
    fun observeRecentScans(): Flow<List<ScanEntity>>

    @Query("SELECT * FROM scans ORDER BY timestamp DESC LIMIT 1")
    suspend fun latestScan(): ScanEntity?

    @Insert
    suspend fun insert(scan: ScanEntity)

    @Query("DELETE FROM scans WHERE id NOT IN (SELECT id FROM scans ORDER BY timestamp DESC LIMIT 10)")
    suspend fun trimHistory()

    @Query("DELETE FROM scans")
    suspend fun clearHistory()
}
