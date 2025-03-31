package com.android.clock.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.clock.data.model.ClockData
import com.android.clock.data.remote.CurrentTimeResponse
import com.android.clock.data.repository.ClockRepository
import com.android.clock.util.TimeOffsetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.time.LocalDateTime
import java.time.ZoneId


/**
 * ViewModel for clock-related operations
 * Manages clocks, time responses, and settings
 */
class ClockViewModel(
    private val repository: ClockRepository,
    private val appContext: Context
) : ViewModel() {

    public val TIME_STANDARD = 60000L

    // List of all clocks
    private val _clocks = MutableStateFlow<List<ClockData>>(emptyList())
    val clocks: StateFlow<List<ClockData>> = _clocks.asStateFlow()

    // Refresh rate in minutes (default: 5 minutes)
    private val _refreshRate = MutableStateFlow(0)
    val refreshRate: StateFlow<Int> = _refreshRate.asStateFlow()

    // Available time zones
    private val _availableTimeZones = MutableStateFlow<List<String>>(emptyList())
    val availableTimeZones: StateFlow<List<String>> = _availableTimeZones.asStateFlow()

    // Current time responses for all clocks (timezone -> response)
    private val _currentTimeResponses = MutableStateFlow<Map<String, CurrentTimeResponse>>(emptyMap())
    val currentTimeResponses: StateFlow<Map<String, CurrentTimeResponse>> = _currentTimeResponses.asStateFlow()
    
    // Current language (default: English)
    private val _currentLanguage = MutableStateFlow("en")
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()
    
    // Time offset manager to handle cached time calculations
    private val timeOffsetManager = TimeOffsetManager()
    
    // SharedPreferences key constants
    companion object {
        private const val PREF_NAME = "ClockViewModelPrefs"
        private const val KEY_REFRESH_RATE = "refresh_rate"
        private const val KEY_LANGUAGE = "language"
        private const val DEFAULT_REFRESH_RATE = 5
    }

    init {
        loadClocks()
        loadAvailableTimeZones()
        loadSavedSettings()
    }
    
    /**
     * Load saved settings from SharedPreferences
     */
    private fun loadSavedSettings() {
        val prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        
        // Load refresh rate
        val savedRate = prefs.getInt(KEY_REFRESH_RATE, DEFAULT_REFRESH_RATE)
        _refreshRate.value = savedRate
        
        // Note: We don't load language here anymore
        // It's handled by LanguageManager through MainActivity
        
        Log.d("ClockViewModel", "Loaded settings: rate=$savedRate")
    }
    
    /**
     * Save settings to SharedPreferences
     */
    private fun saveSettings() {
        try {
            val prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putInt(KEY_REFRESH_RATE, _refreshRate.value)
                // Note: We don't save language here anymore
                .apply()
            
            Log.d("ClockViewModel", "Saved settings: rate=${_refreshRate.value}")
        } catch (e: Exception) {
            Log.e("ClockViewModel", "Error saving settings", e)
        }
    }

    /**
     * Load all clocks from repository
     */
    private fun loadClocks() {
        viewModelScope.launch {
            repository.getAllClocks().collect { clocks ->
                _clocks.value = clocks
                // Initialize time data for all clocks
                if (clocks.isNotEmpty()) {
                    initializeTimeData(clocks)
                }
            }
        }
    }
    
    /**
     * Initialize time data for all clocks (one-time API call)
     */
    private fun initializeTimeData(clocks: List<ClockData>) {
        // Start regular updates immediately to ensure correct refresh rate
        startRegularTimeUpdates()
        
        viewModelScope.launch(Dispatchers.IO) {
            for (clock in clocks) {
                // Only fetch if we don't have an offset already
                if (timeOffsetManager.getOffset(clock.timezone) == null) {
                    initializeClockTimeWithRetry(clock.timezone)
                }
            }
            
            // Mark calibration complete
            timeOffsetManager.markCalibrated()
            
            // Immediately update all clocks after initialization
            updateAllClockTimes()
        }
    }
    
    /**
     * Initialize clock time with retry logic
     */
    private suspend fun initializeClockTimeWithRetry(timezone: String, maxRetries: Int = 3) {
        var retryCount = 0
        var success = false
        
        while (retryCount < maxRetries && !success) {
            try {
                val response = repository.getCurrentTimeByTimeZone(timezone)
                timeOffsetManager.updateOffset(timezone, response)
                
                val updatedResponses = _currentTimeResponses.value.toMutableMap()
                updatedResponses[timezone] = response
                _currentTimeResponses.value = updatedResponses
                
                success = true
                Log.d("ClockViewModel", "Successfully initialized time for $timezone")
            } catch (e: Exception) {
                retryCount++
                Log.e("ClockViewModel", "Error initializing time for $timezone (retry $retryCount/$maxRetries)", e)
                
                if (retryCount < maxRetries) {
                    // Exponential backoff
                    delay(1000L * retryCount)
                } else {
                    // Use local time as fallback
                    generateFallbackTimeOffset(timezone)
                }
            }
        }
    }
    
    /**
     * Generate fallback time offset using local timezone calculation
     */
    private fun generateFallbackTimeOffset(timezone: String) {
        try {
            Log.d("ClockViewModel", "Using fallback local time calculation for $timezone")
            
            // Get current time in device timezone
            val localCalendar = Calendar.getInstance()
            
            // Get current time in target timezone
            val targetZoneId = ZoneId.of(timezone)
            val localZoneId = ZoneId.systemDefault()
            
            val now = LocalDateTime.now()
            val localInstant = now.atZone(localZoneId).toInstant()
            
            // Calculate time in target timezone
            val targetDateTime = LocalDateTime.ofInstant(localInstant, targetZoneId)
            
            // Create response object from target time
            val response = CurrentTimeResponse(
                year = targetDateTime.year,
                month = targetDateTime.monthValue,
                day = targetDateTime.dayOfMonth,
                hour = targetDateTime.hour,
                minute = targetDateTime.minute,
                seconds = targetDateTime.second,
                milliSeconds = 0,
                dateTime = "",
                date = "",
                time = "",
                timeZone = timezone,
                dayOfWeek = "",
                dstActive = false
            )
            
            // Calculate and store offset
            timeOffsetManager.updateOffset(timezone, response)
            
            // Update UI
            val updatedResponses = _currentTimeResponses.value.toMutableMap()
            updatedResponses[timezone] = response
            _currentTimeResponses.value = updatedResponses
            
            Log.d("ClockViewModel", "Successfully created fallback time for $timezone")
        } catch (e: Exception) {
            Log.e("ClockViewModel", "Error creating fallback time for $timezone", e)
        }
    }
    
    /**
     * Update times regularly using local calculation
     */
    private fun startRegularTimeUpdates() {
        viewModelScope.launch {
            // Cancel any existing job first
            try {
                // Immediately perform an update
                updateAllClockTimes()
                
                while (true) {
                    // Check if calibration needed (hourly)
                    if (timeOffsetManager.needsCalibration()) {
                        calibrateTimeOffsets()
                    }
                    
                    // Wait for next update cycle
                    val refreshRateMinutes = _refreshRate.value.toLong().coerceAtLeast(1)
                    Log.d("ClockViewModel", "Next update in $refreshRateMinutes minutes")
                    delay(refreshRateMinutes * TIME_STANDARD) // Convert minutes to ms
                    
                    // Update times
                    updateAllClockTimes()
                }
            } catch (e: Exception) {
                Log.e("ClockViewModel", "Error in regular updates", e)
            }
        }
    }
    
    /**
     * Recalibrate all offsets by calling API
     */
    private fun calibrateTimeOffsets() {
        val currentClocks = _clocks.value
        if (currentClocks.isEmpty()) return
        
        viewModelScope.launch(Dispatchers.IO) {
            Log.d("ClockViewModel", "Starting hourly calibration")
            for (clock in currentClocks) {
                try {
                    val response = repository.getCurrentTimeByTimeZone(clock.timezone)
                    // Update offset
                    timeOffsetManager.updateOffset(clock.timezone, response)
                    
                    // Update UI
                    val updatedResponses = _currentTimeResponses.value.toMutableMap()
                    updatedResponses[clock.timezone] = response
                    _currentTimeResponses.value = updatedResponses
                } catch (e: Exception) {
                    Log.e("ClockViewModel", "Error calibrating time for ${clock.timezone}", e)
                    // No fallback needed here as we already have an offset
                }
            }
            timeOffsetManager.markCalibrated()
            Log.d("ClockViewModel", "Completed hourly calibration")
        }
    }
    
    /**
     * Update all clock times using local calculation
     */
    private fun updateAllClockTimes() {
        val currentClocks = _clocks.value
        val currentTimeMillis = System.currentTimeMillis()
        val updatedResponses = mutableMapOf<String, CurrentTimeResponse>()
        
        for (clock in currentClocks) {
            val offset = timeOffsetManager.getOffset(clock.timezone)
            if (offset != null) {
                // Calculate current time using offset
                val adjustedTimeMillis = currentTimeMillis + offset
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = adjustedTimeMillis
                
                // Create response object
                val response = CurrentTimeResponse(
                    year = calendar.get(Calendar.YEAR),
                    month = calendar.get(Calendar.MONTH) + 1, // Calendar months are 0-based
                    day = calendar.get(Calendar.DAY_OF_MONTH),
                    hour = calendar.get(Calendar.HOUR_OF_DAY),
                    minute = calendar.get(Calendar.MINUTE),
                    seconds = calendar.get(Calendar.SECOND),
                    milliSeconds = calendar.get(Calendar.MILLISECOND),
                    dateTime = "", // Not needed for our use case
                    date = "", // Not needed for our use case
                    time = "", // Not needed for our use case
                    timeZone = clock.timezone,
                    dayOfWeek = "", // Not needed for our use case
                    dstActive = false // Not needed for our use case
                )
                
                updatedResponses[clock.timezone] = response
            }
        }
        
        if (updatedResponses.isNotEmpty()) {
            _currentTimeResponses.value = updatedResponses
        }
    }

    /**
     * Load available time zones
     */
    private fun loadAvailableTimeZones() {
        viewModelScope.launch {
            try {
                _availableTimeZones.value = repository.getAvailableTimeZones()
            } catch (e: Exception) {
                Log.e("ClockViewModel", "loadAvailableTimeZones: ", e)
            }
        }
    }

    /**
     * Set refresh rate in minutes
     */
    fun setRefreshRate(rate: Int) {
        if (_refreshRate.value != rate) {
            _refreshRate.value = rate
            saveSettings()
        }
    }
    
    /**
     * Set application language
     */
    fun setLanguage(language: String) {
        if (_currentLanguage.value != language) {
            _currentLanguage.value = language
            // Note: We don't save language here anymore
            // It's handled by LanguageManager
        }
    }

    /**
     * Add a new clock with timezone and name
     */
    fun addClock(timezone: String, name: String) {
        viewModelScope.launch {
            repository.addClock(ClockData(timezone = timezone, name = name))
            // Initialize time data for the new clock
            initializeClockTime(timezone)
        }
    }
    
    /**
     * Initialize time data for a single clock
     */
    private fun initializeClockTime(timezone: String) {
        viewModelScope.launch(Dispatchers.IO) {
            initializeClockTimeWithRetry(timezone)
        }
    }

    /**
     * Update an existing clock
     */
    fun updateClock(clockData: ClockData) {
        viewModelScope.launch {
            repository.updateClock(clockData)
        }
    }

    /**
     * Delete a clock
     */
    fun deleteClock(clockData: ClockData) {
        viewModelScope.launch {
            repository.deleteClock(clockData)
        }
    }
    
    /**
     * Get current time for a specific timezone
     * Uses offset if available, otherwise calls API
     */
    fun getCurrentTimeByTimeZone(timeZone: String) {
        val offset = timeOffsetManager.getOffset(timeZone)
        if (offset != null) {
            // Use local calculation
            val currentTimeMillis = System.currentTimeMillis()
            val adjustedTimeMillis = currentTimeMillis + offset
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = adjustedTimeMillis
            
            val response = CurrentTimeResponse(
                year = calendar.get(Calendar.YEAR),
                month = calendar.get(Calendar.MONTH) + 1,
                day = calendar.get(Calendar.DAY_OF_MONTH),
                hour = calendar.get(Calendar.HOUR_OF_DAY),
                minute = calendar.get(Calendar.MINUTE),
                seconds = calendar.get(Calendar.SECOND),
                milliSeconds = calendar.get(Calendar.MILLISECOND),
                dateTime = "",
                date = "",
                time = "",
                timeZone = timeZone,
                dayOfWeek = "",
                dstActive = false
            )
            
            val updatedResponses = _currentTimeResponses.value.toMutableMap()
            updatedResponses[timeZone] = response
            _currentTimeResponses.value = updatedResponses
        } else {
            // Call API and initialize offset
            initializeClockTime(timeZone)
        }
    }
    
    /**
     * Get time offset for a timezone - used by floating clock service
     */
    fun getTimeOffset(timezone: String): Long? {
        return timeOffsetManager.getOffset(timezone)
    }
    
    /**
     * Factory for creating ClockViewModel instances
     */
    class Factory(
        private val repository: ClockRepository,
        private val appContext: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ClockViewModel::class.java)) {
                return ClockViewModel(repository, appContext) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
    
    // Suspend functions need to import
    private suspend fun delay(timeMillis: Long) {
        withContext(Dispatchers.IO) {
            kotlinx.coroutines.delay(timeMillis)
        }
    }
} 