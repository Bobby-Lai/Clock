package com.android.clock.ui.screen

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.clock.MainActivity
import com.android.clock.R
import com.android.clock.data.model.ClockData
import com.android.clock.data.remote.CurrentTimeResponse
import com.android.clock.ui.viewmodel.ClockViewModel
import com.android.clock.util.PermissionCheckActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.StringBuilder
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Helper function to get formatted local time
private fun getFormattedLocalTime(timezone: String): String {
    val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    val localTime = java.time.LocalTime.now(ZoneId.of(timezone))
    return formatter.format(localTime)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimesScreen(viewModel: ClockViewModel) {
    val context = LocalContext.current
    val clocks by viewModel.clocks.collectAsState()
    val currentTimeResponses by viewModel.currentTimeResponses.collectAsState()
    val currentLanguage by viewModel.currentLanguage.collectAsState()
    val refreshRateState by viewModel.refreshRate.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var showLanguageDialog by remember { mutableStateOf(false) }
    
    // Store time responses for each clock
    val timeResponses = remember { mutableStateMapOf<Long, CurrentTimeResponse?>() }
    
    // Update local timeResponses when ViewModel data changes
    LaunchedEffect(currentTimeResponses) {
        clocks.forEach { clock ->
            currentTimeResponses[clock.timezone]?.let { response ->
                timeResponses[clock.id] = response
            }
        }
    }
    
    // Show toast when refresh rate changes (not on initial load)
    LaunchedEffect(refreshRateState) {
        // Only show toast if this is not the first time loading
        if (timeResponses.isNotEmpty()) {
            coroutineScope.launch {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Refresh rate: $refreshRateState min",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    // Cleanup when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            // No need to cancel jobs here as ViewModel manages the updates
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.times)) },
                actions = {
                    IconButton(onClick = { showLanguageDialog = true }) {
                        Icon(Icons.Default.Language, contentDescription = stringResource(R.string.language))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
        ) {
            // Refresh Rate button row
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.refresh_rate),
                        style = MaterialTheme.typography.titleSmall
                    )

                    // Rate options: 1, 5, 10 mins
                    listOf(1, 5, 10).forEach { rate ->
                        RefreshRateButton(
                            rate = rate,
                            selected = refreshRateState == rate,
                            onClick = {
                                viewModel.setRefreshRate(rate)
                            }
                        )
                    }

                    Text(
                        text = stringResource(R.string.min),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
            
            if (clocks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.no_data))
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(clocks) { clock ->
                        ClockItem(
                            clockData = clock,
                            viewModel = viewModel,
                            currentTimeResponse = timeResponses[clock.id]
                        )
                    }
                }
            }
        }
    }

    if (showLanguageDialog) {
        LanguageDialog(
            currentLanguage = currentLanguage,
            onDismiss = { showLanguageDialog = false },
            onLanguageSelected = { selectedLanguage ->
                Log.d("TimesScreen", "Language selected: $selectedLanguage (current: $currentLanguage)")
                showLanguageDialog = false
                
                // Check if language is actually different
                if (!selectedLanguage.equals(currentLanguage, ignoreCase = true)) {
                    // Force update the language in ViewModel
                    viewModel.setLanguage(selectedLanguage)
                    
                    // Use simplified approach to restart the app
                    coroutineScope.launch {
                        delay(500) // Longer delay to ensure settings are saved
                        
                        try {
                            // Find activity and use the helper method
                            val activity = context.findActivity()
                            if (activity != null) {
                                Log.d("TimesScreen", "Restarting app to apply language: $selectedLanguage")
                                // Use the helper method in MainActivity
                                MainActivity.restartApp(activity)
                            } else {
                                Log.e("TimesScreen", "Could not find activity")
                                // Fallback - try to restart using application context
                                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                                intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            }
                        } catch (e: Exception) {
                            Log.e("TimesScreen", "Error restarting app", e)
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun RefreshRateButton(
    rate: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        RadioButton(
            selected = selected,
            onClick = { onClick() }
        )
        Text(
            text = "$rate",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun ClockItem(
    clockData: ClockData,
    viewModel: ClockViewModel,
    currentTimeResponse: CurrentTimeResponse?
) {
    val context = LocalContext.current
    val formattedTime = currentTimeResponse?.let { response ->
        String.format("%02d:%02d:%02d", response.hour, response.minute, response.seconds)
    } ?: getFormattedLocalTime(clockData.timezone)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                // Launch permission check activity which will start the floating clock service
                PermissionCheckActivity.start(
                    context = context,
                    clockName = clockData.name,
                    clockTime = formattedTime,
                    timezone = clockData.timezone,
                    refreshRate = viewModel.refreshRate.value,
                    viewModel = viewModel
                )
            }
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Clock name
            Text(
                text = clockData.name,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            // Timezone info
            Text(
                text = clockData.timezone,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            // Time display (HH:MM:SS format)
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
}

@Composable
fun LanguageDialog(
    currentLanguage: String,
    onDismiss: () -> Unit,
    onLanguageSelected: (String) -> Unit
) {
    val context = LocalContext.current
    
    // Log actual selected language for debugging
    Log.d("LanguageDialog", "Current language in dialog: $currentLanguage")
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language)) },
        text = {
            Column {
                listOf("en", "zh-TW").forEach { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLanguageSelected(language) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (language) {
                                "en" -> "English"
                                "zh-TW" -> "繁體中文"
                                else -> language
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        
                        // 檢查並顯示當前語言的選中狀態
                        if (language.equals(currentLanguage, ignoreCase = true)) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// Helper to find activity from context
fun Context.findActivity(): android.app.Activity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is android.app.Activity) {
            return context
        }
        context = context.baseContext
    }
    return null
} 