package com.safetrack.data.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class LocationData(
    val userId: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0f,
    val speed: Float = 0f,
    val altitude: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val sharingStatus: String = STATUS_ACTIVE, // ACTIVE, PAUSED, STOPPED
    val batteryPct: Int = -1,
    val isOnline: Boolean = true,
    val isApproximate: Boolean = false,
    val provider: String = "fused"
) {
    companion object {
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_PAUSED = "PAUSED"
        const val STATUS_STOPPED = "STOPPED"
    }
}
