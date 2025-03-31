package com.android.clock.util

import android.util.Log
import com.android.clock.data.remote.CurrentTimeResponse
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Manages time offsets between remote API time and local device time
 */
class TimeOffsetManager {
    // Store timezone offsets (timezone -> offset in ms)
    private val timeOffsets = ConcurrentHashMap<String, Long>()

    private val CALIBRATION_THRESHOLD = TimeUnit.HOURS.toMillis(1)
    // Track last calibration time
    private var lastCalibrationTime = 0L
    
    // Get offset for specific timezone
    fun getOffset(timezone: String): Long? = timeOffsets[timezone]
    
    // Calculate and store time offset
    fun updateOffset(timezone: String, remoteTime: CurrentTimeResponse) {
        val remoteTimeMillis = convertToMillis(remoteTime)
        val localTimeMillis = System.currentTimeMillis()
        val offset = remoteTimeMillis - localTimeMillis
        timeOffsets[timezone] = offset
        Log.d("TimeOffsetManager", "Updated offset for $timezone: $offset ms")
    }
    
    // Set offset manually
    fun setManualOffset(timezone: String, offset: Long) {
        timeOffsets[timezone] = offset
    }
    
    // Check if calibration needed (hourly)
    fun needsCalibration(): Boolean {
        val now = System.currentTimeMillis()
        return now - lastCalibrationTime > CALIBRATION_THRESHOLD
    }
    
    // Mark calibration complete
    fun markCalibrated() {
        lastCalibrationTime = System.currentTimeMillis()
    }
    
    // Clear all offset data
    fun clearOffsets() {
        timeOffsets.clear()
    }
    
    // Helper: Convert API time to milliseconds
    private fun convertToMillis(timeResponse: CurrentTimeResponse): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, timeResponse.hour)
        calendar.set(Calendar.MINUTE, timeResponse.minute)
        calendar.set(Calendar.SECOND, timeResponse.seconds)
        return calendar.timeInMillis
    }
} 