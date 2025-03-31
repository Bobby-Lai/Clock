package com.android.clock.util

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

// Extension property to access DataStore from Context
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Manages language settings for the application
 * Handles persistence and application of locale changes
 */
class LanguageManager(
    private val context: Context
) {
    // Key for storing language preference
    private val LANGUAGE_KEY = stringPreferencesKey("language")
    
    // SharedPreferences for immediate access
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "language_prefs", Context.MODE_PRIVATE
    )
    
    // Constants
    companion object {
        const val PREF_LANGUAGE = "language"
        const val DEFAULT_LANGUAGE = "en"
    }

    // Flow that emits the current language setting, defaulting to English
    val currentLanguage: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[LANGUAGE_KEY] ?: DEFAULT_LANGUAGE
        }
    
    /**
     * Get current language immediately (non-suspending)
     */
    fun getCurrentLanguage(): String {
        val savedLanguage = prefs.getString(PREF_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
        Log.d("LanguageManager", "getCurrentLanguage() = $savedLanguage (from SharedPreferences)")
        
        // Check if locale matches saved language
        val currentLocale = Locale.getDefault()
        val currentLanguageCode = if (currentLocale.language == "zh" && currentLocale.country == "TW") {
            "zh-TW"
        } else {
            currentLocale.language
        }
        
        if (currentLanguageCode != savedLanguage) {
            Log.w("LanguageManager", "Current locale ($currentLanguageCode) doesn't match saved language ($savedLanguage)")
            // Force update to match actual locale
            prefs.edit().putString(PREF_LANGUAGE, currentLanguageCode).commit()
            return currentLanguageCode
        }
        
        return savedLanguage
    }

    /**
     * Set the application language and persist the setting
     * Also updates the locale immediately
     */
    suspend fun setLanguage(language: String) {
        Log.d("LanguageManager", "Setting language to: $language")
        
        // Save to DataStore for Flow consumers
        context.dataStore.edit { preferences ->
            preferences[LANGUAGE_KEY] = language
        }
        
        // Save to SharedPreferences for immediate access
        prefs.edit().putString(PREF_LANGUAGE, language).commit() // Use commit instead of apply for immediate effect
        
        // Apply locale change
        updateLocale(language)
    }
    
    /**
     * Set language without suspending (for use from non-coroutine contexts)
     */
    fun setLanguageSync(language: String) {
        Log.d("LanguageManager", "Setting language synchronously to: $language")
        
        // Save to SharedPreferences
        prefs.edit().putString(PREF_LANGUAGE, language).commit() // Use commit instead of apply for immediate effect
        
        // Apply locale change
        updateLocale(language)
    }

    /**
     * Update the application's locale based on language code
     * Returns the new configuration context
     */
    fun updateLocale(language: String): Context {
        Log.d("LanguageManager", "Updating locale to: $language")
        
        val locale = when (language) {
            "zh-TW" -> Locale("zh", "TW")
            else -> Locale("en")
        }

        // Update default locale
        Locale.setDefault(locale)
        
        // Create new configuration
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        // Create and return configuration context
        val configContext = context.createConfigurationContext(config)
        
        // Update resources configuration (for compatibility)
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
        
        return configContext
    }
} 