package com.safetrack.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "location_records")
data class LocationRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val speed: Float,
    val altitude: Double,
    val timestamp: Long,
    val sharingStatus: String,
    val batteryPct: Int,
    val isSynced: Boolean = false
)
