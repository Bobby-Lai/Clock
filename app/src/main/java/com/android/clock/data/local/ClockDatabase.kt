package com.android.clock.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.android.clock.data.model.ClockData

@Database(entities = [ClockData::class], version = 1)
abstract class ClockDatabase : RoomDatabase() {
    abstract fun clockDao(): ClockDao
} 