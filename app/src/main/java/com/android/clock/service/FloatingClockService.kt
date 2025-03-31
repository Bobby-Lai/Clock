package com.android.clock.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.android.clock.MainActivity
import com.android.clock.R
import com.android.clock.util.TimeOffsetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Service for displaying floating clocks
 * Supports multiple clocks (max 5) and automatic time updates
 */
class FloatingClockService : Service() {
    
    private var windowManager: WindowManager? = null
    private val floatingViews = mutableMapOf<String, View>() // timezone -> View
    private val layoutParams = mutableMapOf<String, WindowManager.LayoutParams>() // timezone -> params
    
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private val initialPositions = mutableMapOf<String, Pair<Int, Int>>() // timezone -> (x, y)
    
    private val timeOffsetManager = TimeOffsetManager()
    private var updateJob: Job? = null
    private var refreshRateMinutes = 1 // Default refresh rate
    
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    
    companion object {
        private const val CHANNEL_ID = "floating_clock_channel"
        private const val NOTIFICATION_ID = 1001
        const val MAX_FLOATING_CLOCKS = 5
        
        const val EXTRA_CLOCK_NAME = "extra_clock_name"
        const val EXTRA_CLOCK_TIME = "extra_clock_time"
        const val EXTRA_TIMEZONE = "extra_timezone"
        const val EXTRA_TIME_OFFSET = "extra_time_offset"
        const val EXTRA_REFRESH_RATE = "extra_refresh_rate"
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val clockName = intent.getStringExtra(EXTRA_CLOCK_NAME) ?: "Clock"
            val clockTime = intent.getStringExtra(EXTRA_CLOCK_TIME) ?: "00:00:00"
            val timezone = intent.getStringExtra(EXTRA_TIMEZONE) ?: ""
            val offset = intent.getLongExtra(EXTRA_TIME_OFFSET, 0L)
            val refreshRate = intent.getIntExtra(EXTRA_REFRESH_RATE, 1)
            
            // Update refresh rate if provided
            if (refreshRate > 0) {
                refreshRateMinutes = refreshRate
            }
            
            Log.d("FloatingClock", "Adding floating clock for $timezone")
            
            // Check if we can add more clocks (limit: 5)
            if (timezone.isNotEmpty() && (floatingViews.size < MAX_FLOATING_CLOCKS || floatingViews.containsKey(timezone))) {
                // Store time offset
                timeOffsetManager.setManualOffset(timezone, offset)
                
                // Add floating view
                addOrUpdateFloatingClock(timezone, clockName, clockTime)
                
                // Start update job if not already running
                startTimeUpdateJob()
            } else if (floatingViews.size >= MAX_FLOATING_CLOCKS) {
                Toast.makeText(this, "Maximum number of floating clocks reached (5)", Toast.LENGTH_SHORT).show()
            }
        }
        
        return START_STICKY
    }
    
    /**
     * Add or update a floating clock for a timezone
     */
    private fun addOrUpdateFloatingClock(timezone: String, clockName: String, clockTime: String) {
        // Remove existing view for this timezone if any
        if (floatingViews.containsKey(timezone)) {
            removeFloatingView(timezone)
        }
        
        // Create new floating view
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val floatingView = inflater.inflate(R.layout.layout_floating_clock, null)
        
        // Set clock details
        val tvClockName = floatingView.findViewById<TextView>(R.id.tvFloatingClockName)
        val tvClockTime = floatingView.findViewById<TextView>(R.id.tvFloatingClockTime)
        val btnClose = floatingView.findViewById<ImageView>(R.id.btnCloseFloating)
        
        tvClockName.text = clockName
        tvClockTime.text = clockTime
        
        // Set close button click listener
        btnClose.setOnClickListener {
            removeFloatingView(timezone)
            
            // Stop service if no more clocks
            if (floatingViews.isEmpty()) {
                stopSelf()
            }
        }
        
        // Set click listener on clock view to open main app
        floatingView.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
        
        // Create layout params for this view
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
            else 
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        
        // Position based on number of existing windows
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100 + (floatingViews.size * 200) // Stagger vertically
        
        // Set touch listener for drag
        floatingView.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Store initial position
                    initialPositions[timezone] = Pair(params.x, params.y)
                    // Store touch position
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // Calculate new position
                    val initialPosition = initialPositions[timezone] ?: Pair(0, 0)
                    params.x = initialPosition.first + (event.rawX - initialTouchX).toInt()
                    params.y = initialPosition.second + (event.rawY - initialTouchY).toInt()
                    
                    // Update view layout
                    windowManager?.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }
        
        // Add view to window
        try {
            windowManager?.addView(floatingView, params)
            floatingViews[timezone] = floatingView
            layoutParams[timezone] = params
            
            Log.d("FloatingClock", "Added floating clock for $timezone, total: ${floatingViews.size}")
        } catch (e: Exception) {
            Log.e("FloatingClock", "Error adding floating view for $timezone", e)
        }
    }
    
    /**
     * Remove a floating view for a specific timezone
     */
    private fun removeFloatingView(timezone: String) {
        val view = floatingViews[timezone] ?: return
        
        try {
            windowManager?.removeView(view)
            floatingViews.remove(timezone)
            layoutParams.remove(timezone)
            initialPositions.remove(timezone)
            
            Log.d("FloatingClock", "Removed floating clock for $timezone, remaining: ${floatingViews.size}")
        } catch (e: Exception) {
            Log.e("FloatingClock", "Error removing floating view for $timezone", e)
        }
    }
    
    /**
     * Remove all floating views
     */
    private fun removeAllFloatingViews() {
        val timezonesToRemove = floatingViews.keys.toList()
        for (timezone in timezonesToRemove) {
            removeFloatingView(timezone)
        }
    }
    
    /**
     * Start job to update clock times regularly
     */
    private fun startTimeUpdateJob() {
        // Cancel existing job if any
        updateJob?.cancel()
        
        // Start new job
        updateJob = serviceScope.launch {
            while (true) {
                updateAllClockTimes()
                delay(TimeUnit.MINUTES.toMillis(refreshRateMinutes.toLong()))
            }
        }
    }
    
    /**
     * Update all clock times using stored offsets
     */
    private fun updateAllClockTimes() {
        val currentTimeMillis = System.currentTimeMillis()
        
        for ((timezone, view) in floatingViews) {
            val offset = timeOffsetManager.getOffset(timezone) ?: continue
            
            // Calculate current time
            val adjustedTimeMillis = currentTimeMillis + offset
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = adjustedTimeMillis
            
            val timeText = String.format(
                "%02d:%02d:%02d",
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                calendar.get(Calendar.SECOND)
            )
            
            // Update time display
            val tvClockTime = view.findViewById<TextView>(R.id.tvFloatingClockTime)
            tvClockTime?.text = timeText
        }
    }
    
    /**
     * Create notification channel for Android O+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Floating Clock"
            val description = "Shows clock in a floating window"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            channel.description = description
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Create foreground service notification
     */
    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("Floating Clock")
        .setContentText("${floatingViews.size} clocks displayed")
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()
    
    override fun onDestroy() {
        super.onDestroy()
        updateJob?.cancel()
        serviceScope.cancel()
        removeAllFloatingViews()
    }
} 