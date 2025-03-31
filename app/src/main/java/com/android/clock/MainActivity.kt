package com.android.clock

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import com.android.clock.data.local.ClockDatabase
import com.android.clock.data.repository.ClockRepository
import com.android.clock.ui.screen.SettingScreen
import com.android.clock.ui.screen.TimesScreen
import com.android.clock.ui.theme.ClockTheme
import com.android.clock.ui.viewmodel.ClockViewModel
import com.android.clock.util.LanguageManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

// CompositionLocal to provide language context
val LocalLanguageContext = staticCompositionLocalOf<Context> { error("No context provided") }

class MainActivity : ComponentActivity() {

    private lateinit var clockRepository: ClockRepository
    private lateinit var clockViewModel: ClockViewModel
    private lateinit var languageManager: LanguageManager
    
    companion object {
        private const val TAG = "MainActivity"
        
        // Language change helper method
        fun restartApp(activity: Activity) {
            Log.d(TAG, "Restarting app to apply language change")
            val intent = Intent(activity, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.finish()
            activity.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        val database = Room.databaseBuilder(
            applicationContext,
            ClockDatabase::class.java,
            "clock_database"
        ).build()

        clockRepository = ClockRepository(database.clockDao())
        languageManager = LanguageManager(applicationContext)

        // Read current language BEFORE setting up ViewModel
        val currentLanguage = languageManager.getCurrentLanguage()
        Log.d(TAG, "Current language from LanguageManager: $currentLanguage")
        
        // Check actual system locale
        val currentLocale = Locale.getDefault()
        val actualLanguage = if (currentLocale.language == "zh" && currentLocale.country == "TW") {
            "zh-TW"
        } else {
            currentLocale.language
        }
        Log.d(TAG, "Actual system locale: $actualLanguage")
        
        // Apply locale configuration - use actual system locale to ensure consistency
        val contextWithLocale = languageManager.updateLocale(actualLanguage)
        
        // Copy the configuration to the current resources (instead of reassigning)
        val config = Configuration(contextWithLocale.resources.configuration)
        val metrics = resources.displayMetrics
        resources.updateConfiguration(config, metrics)

        // Initialize ViewModel with proper language
        val factory = ClockViewModel.Factory(clockRepository, applicationContext)
        clockViewModel = ViewModelProvider(this, factory)[ClockViewModel::class.java]
        clockViewModel.setLanguage(actualLanguage)

        enableEdgeToEdge()
        setContent {
            val language by clockViewModel.currentLanguage.collectAsState()
            Log.d(TAG, "Compose with language: $language")
            
            // Create new configuration context when language changes
            val languageContext = remember(language) {
                Log.d(TAG, "Creating new context for: $language")
                languageManager.updateLocale(language)
            }

            // Provide language context to Compose
            CompositionLocalProvider(LocalLanguageContext provides languageContext) {
                ClockTheme {
                    MainScreen(clockViewModel)
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        // Only log the current state for debugging
        val currentLanguage = languageManager.getCurrentLanguage()
        Log.d(TAG, "Current language on resume: $currentLanguage")
    }

    // Maintain language settings when configuration changes
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "onConfigurationChanged")
        
        // Use current language from LanguageManager
        val currentLanguage = languageManager.getCurrentLanguage()
        Log.d(TAG, "Applying language on config change: $currentLanguage")
        languageManager.updateLocale(currentLanguage)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: ClockViewModel) {
    // Use language context
    val context = LocalLanguageContext.current
    val navController = rememberNavController()
    var selectedItem by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Timer, contentDescription = null) },
                    label = { Text(stringResource(R.string.times)) },
                    selected = selectedItem == 0,
                    onClick = {
                        selectedItem = 0
                        navController.navigate("times") {
                            popUpTo("times") { inclusive = true }
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.setting)) },
                    selected = selectedItem == 1,
                    onClick = {
                        selectedItem = 1
                        navController.navigate("setting") {
                            popUpTo("setting") { inclusive = true }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "times",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("times") {
                TimesScreen(viewModel)
            }
            composable("setting") {
                SettingScreen(viewModel)
            }
        }
    }
}