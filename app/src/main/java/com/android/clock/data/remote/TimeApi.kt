package com.android.clock.data.remote

import android.util.Log
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// Data class for current time response
data class CurrentTimeResponse(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val seconds: Int,
    val milliSeconds: Int,
    val dateTime: String,
    val date: String,
    val time: String,
    val timeZone: String,
    val dayOfWeek: String,
    val dstActive: Boolean
)

interface TimeApiService {
    // Get list of available timezones
    @GET("TimeZone/AvailableTimeZones")
    suspend fun getAvailableTimeZones(): List<String>
    
    // Get current time for a specific timezone
    @GET("Time/current/zone")
    suspend fun getCurrentTimeByTimeZone(@Query("timeZone") timeZone: String): CurrentTimeResponse
}

object TimeApi {
    // Configure OkHttpClient with timeouts and connection settings
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)  // Connection timeout
        .readTimeout(10, TimeUnit.SECONDS)     // Read timeout
        .writeTimeout(10, TimeUnit.SECONDS)    // Write timeout
        .retryOnConnectionFailure(true)        // Retry on connection failures
        .build()
        
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://timeapi.io/api/")
        .client(okHttpClient)  // Use the configured client
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val timeApiService = retrofit.create(TimeApiService::class.java)

    suspend fun getCurrentTimeByTimeZone(timeZone: String): CurrentTimeResponse {
        Log.i("TimeApi", "Getting time for timezone: $timeZone")
        try {
            val response = timeApiService.getCurrentTimeByTimeZone(timeZone)
            Log.d("TimeApi", "Successfully retrieved time for $timeZone")
            return response
        } catch (e: Exception) {
            Log.e("TimeApi", "Error retrieving time for $timeZone", e)
            throw e
        }
    }

    suspend fun getAvailableTimeZones(): List<String> {
        Log.i("TimeApi", "Getting available timezones")
        try {
            val timezones = timeApiService.getAvailableTimeZones()
            Log.d("TimeApi", "Retrieved ${timezones.size} timezones")
            return timezones
        } catch (e: Exception) {
            Log.e("TimeApi", "Error retrieving available timezones", e)
            throw e
        }
    }
}