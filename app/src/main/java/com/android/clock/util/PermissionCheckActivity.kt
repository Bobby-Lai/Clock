package com.android.clock.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.clock.R
import com.android.clock.service.FloatingClockService
import com.android.clock.ui.theme.ClockTheme
import com.android.clock.ui.viewmodel.ClockViewModel

/**
 * Activity to check and request overlay permission before starting floating clock
 */
class PermissionCheckActivity : ComponentActivity() {
    
    companion object {
        private const val REQUEST_CODE_OVERLAY_PERMISSION = 1001
        const val EXTRA_CLOCK_NAME = "extra_clock_name"
        const val EXTRA_CLOCK_TIME = "extra_clock_time"
        const val EXTRA_TIMEZONE = "extra_timezone"
        const val EXTRA_REFRESH_RATE = "extra_refresh_rate"
        
        /**
         * Start permission check activity with clock data
         */
        fun start(
            context: Context, 
            clockName: String, 
            clockTime: String, 
            timezone: String,
            refreshRate: Int = 1,
            viewModel: ClockViewModel? = null
        ) {
            val intent = Intent(context, PermissionCheckActivity::class.java).apply {
                putExtra(EXTRA_CLOCK_NAME, clockName)
                putExtra(EXTRA_CLOCK_TIME, clockTime)
                putExtra(EXTRA_TIMEZONE, timezone)
                putExtra(EXTRA_REFRESH_RATE, refreshRate)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            // Store offset from ViewModel if available
            viewModel?.getTimeOffset(timezone)?.let { offset ->
                intent.putExtra(FloatingClockService.EXTRA_TIME_OFFSET, offset)
            }
            
            context.startActivity(intent)
        }
    }
    
    private var clockName: String = ""
    private var clockTime: String = ""
    private var timezone: String = ""
    private var refreshRate: Int = 1
    private var timeOffset: Long = 0L
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Get data from intent
        clockName = intent.getStringExtra(EXTRA_CLOCK_NAME) ?: "Clock"
        clockTime = intent.getStringExtra(EXTRA_CLOCK_TIME) ?: "00:00:00"
        timezone = intent.getStringExtra(EXTRA_TIMEZONE) ?: ""
        refreshRate = intent.getIntExtra(EXTRA_REFRESH_RATE, 1)
        timeOffset = intent.getLongExtra(FloatingClockService.EXTRA_TIME_OFFSET, 0L)
        
        setContent {
            ClockTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PermissionCheckScreen()
                }
            }
        }
        
        // Check permission immediately
        if (hasOverlayPermission()) {
            startFloatingClockService()
            finish()
        }
    }
    
    /**
     * Check if we have overlay permission
     */
    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true // Permission granted by default on pre-M devices
        }
    }
    
    /**
     * Launch system settings to request overlay permission
     */
    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, REQUEST_CODE_OVERLAY_PERMISSION)
        }
    }
    
    /**
     * Start floating clock service
     */
    private fun startFloatingClockService() {
        val intent = Intent(this, FloatingClockService::class.java).apply {
            putExtra(FloatingClockService.EXTRA_CLOCK_NAME, clockName)
            putExtra(FloatingClockService.EXTRA_CLOCK_TIME, clockTime)
            putExtra(FloatingClockService.EXTRA_TIMEZONE, timezone)
            putExtra(FloatingClockService.EXTRA_TIME_OFFSET, timeOffset)
            putExtra(FloatingClockService.EXTRA_REFRESH_RATE, refreshRate)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_OVERLAY_PERMISSION) {
            if (hasOverlayPermission()) {
                startFloatingClockService()
                finish()
            }
        }
    }
    
    @Composable
    fun PermissionCheckScreen() {
        val context = LocalContext.current
        
        LaunchedEffect(Unit) {
            if (hasOverlayPermission()) {
                startFloatingClockService()
                (context as? Activity)?.finish()
            }
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.permission_required),
                style = MaterialTheme.typography.headlineSmall
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = stringResource(R.string.overlay_permission_message),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = { requestOverlayPermission() }
            ) {
                Text(stringResource(R.string.grant_permission))
            }
        }
    }
} 