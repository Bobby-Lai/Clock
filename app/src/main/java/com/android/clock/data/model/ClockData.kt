package com.android.clock.data.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "clocks")
data class ClockData(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timezone: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
) : Parcelable