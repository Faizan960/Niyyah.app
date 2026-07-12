package com.salahlock.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prayer_time_cache")
data class PrayerTimeCacheEntity(
    @PrimaryKey
    val dateString: String, // e.g., "21-06-2026"
    val fajr: String, // e.g., "04:30 (IST)"
    val sunrise: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String,
    val calculationMethod: Int,
    val lat: Double,
    val lng: Double,
    val lastUpdated: Long
)
