package com.safetrack.data.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class SosAlert(
    val alertId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderPhone: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0f,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = STATUS_ACTIVE, // ACTIVE, RESOLVED, CANCELLED
    val message: String = "EMERGENCY: I need immediate assistance!",
    val batteryPct: Int = -1,
    val resolvedAt: Long = 0L,
    val resolutionNotes: String = ""
) {
    companion object {
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_RESOLVED = "RESOLVED"
        const val STATUS_CANCELLED = "CANCELLED"
    }
}
