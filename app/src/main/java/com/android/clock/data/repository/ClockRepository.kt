package com.android.clock.data.repository

import com.android.clock.data.local.ClockDao
import com.android.clock.data.model.ClockData
import com.android.clock.data.remote.CurrentTimeResponse
import com.android.clock.data.remote.TimeApi
import kotlinx.coroutines.flow.Flow

class ClockRepository(
    private val clockDao: ClockDao,
//    private val timeApi: TimeApi
) {
    fun getAllClocks(): Flow<List<ClockData>> = clockDao.getAllClocks()

    suspend fun addClock(clockData: ClockData) = clockDao.insertClock(clockData)

    suspend fun updateClock(clockData: ClockData) = clockDao.updateClock(clockData)

    suspend fun deleteClock(clockData: ClockData) = clockDao.deleteClock(clockData)

    suspend fun getAvailableTimeZones(): List<String> = TimeApi.getAvailableTimeZones()
    
    // Add method to get current time for a specific timezone
    suspend fun getCurrentTimeByTimeZone(timeZone: String): CurrentTimeResponse {
        return TimeApi.getCurrentTimeByTimeZone(timeZone)
    }
} 