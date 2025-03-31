package com.android.clock.di

import android.content.Context
import androidx.room.Room
import com.android.clock.data.local.ClockDatabase
import com.android.clock.data.remote.TimeApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideClockDatabase(
        @ApplicationContext context: Context
    ): ClockDatabase {
        return Room.databaseBuilder(
            context,
            ClockDatabase::class.java,
            "clock_database"
        ).build()
    }

    @Provides
    @Singleton
    fun provideClockDao(database: ClockDatabase) = database.clockDao()

    @Provides
    @Singleton
    fun provideTimeApi(): TimeApi {
        return TimeApi
//        Retrofit.Builder()
//            .baseUrl("https://timeapi.io/api/")
//            .addConverterFactory(GsonConverterFactory.create())
//            .build()
//            .create(TimeApi::class.java)
    }
} 