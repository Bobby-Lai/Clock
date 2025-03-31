package com.android.clock.data.local

import androidx.room.*
import com.android.clock.data.model.ClockData
import kotlinx.coroutines.flow.Flow

@Dao
interface ClockDao {
    @Query("SELECT * FROM clocks ORDER BY createdAt DESC")
    fun getAllClocks(): Flow<List<ClockData>>

    @Insert
    suspend fun insertClock(clockData: ClockData)

    @Update
    suspend fun updateClock(clockData: ClockData)

    @Delete
    suspend fun deleteClock(clockData: ClockData)

    @Query("DELETE FROM clocks")
    suspend fun deleteAllClocks()
} 