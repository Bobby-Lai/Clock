package com.android.clock

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import com.android.clock.util.LanguageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Locale

class ClockApplication : Application() {
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var languageManager: LanguageManager
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize LanguageManager
        languageManager = LanguageManager(applicationContext)
        
        // Apply saved language settings immediately on app startup
        try {
            val language = runBlocking { 
                languageManager.currentLanguage.first()
            }
            Log.d("ClockApplication", "Loading saved language: $language")
            updateLocale(language)
        } catch (e: Exception) {
            Log.e("ClockApplication", "Error loading language", e)
        }
    }
    
    override fun attachBaseContext(base: Context) {
        // Try to read language settings from DataStore
        val language = try {
            runBlocking { 
                val manager = LanguageManager(base)
                manager.currentLanguage.first()
            }
        } catch (e: Exception) {
            Log.e("ClockApplication", "Failed to get language in attachBaseContext", e)
            "en" // Use default language
        }
        
        // Create configuration with the retrieved language setting
        val locale = when (language) {
            "zh-TW" -> Locale("zh", "TW")
            else -> Locale("en")
        }
        
        // Set default Locale
        Locale.setDefault(locale)
        
        // Create new Configuration
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        
        // Create new Context with updated configuration
        val newContext = base.createConfigurationContext(config)
        
        super.attachBaseContext(newContext)
    }
    
    private fun updateLocale(language: String) {
        val locale = when (language) {
            "zh-TW" -> Locale("zh", "TW")
            else -> Locale("en")
        }
        
        Locale.setDefault(locale)
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        
        // Update resource configuration
        resources.updateConfiguration(config, resources.displayMetrics)
    }
} 