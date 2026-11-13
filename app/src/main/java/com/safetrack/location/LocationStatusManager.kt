package com.safetrack.location

import com.safetrack.data.model.LocationData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LocationStatusManager {

    enum class LocationState {
        LIVE,
        LAST_KNOWN,
        OFFLINE,
        NO_LOCATION
    }

    private const val LIVE_FRESHNESS_THRESHOLD_MS = 2 * 60 * 1000L // 2 Minutes
    private const val OFFLINE_THRESHOLD_MS = 15 * 60 * 1000L      // 15 Minutes

    fun evaluateState(location: LocationData?): LocationState {
        if (location == null || (location.latitude == 0.0 && location.longitude == 0.0)) {
            return LocationState.NO_LOCATION
        }

        if (location.sharingStatus == LocationData.STATUS_PAUSED ||
            location.sharingStatus == LocationData.STATUS_STOPPED
        ) {
            return LocationState.OFFLINE
        }

        val ageMs = System.currentTimeMillis() - location.timestamp

        return when {
            ageMs <= LIVE_FRESHNESS_THRESHOLD_MS -> LocationState.LIVE
            ageMs <= OFFLINE_THRESHOLD_MS -> LocationState.LAST_KNOWN
            else -> LocationState.OFFLINE
        }
    }

    fun formatStatusLabel(state: LocationState): String {
        return when (state) {
            LocationState.LIVE -> "● LIVE"
            LocationState.LAST_KNOWN -> "● LAST KNOWN"
            LocationState.OFFLINE -> "● OFFLINE"
            LocationState.NO_LOCATION -> "● NO LOCATION"
        }
    }

    fun formatRelativeTime(timestamp: Long): String {
        if (timestamp <= 0L) return "Never updated"
        val diffMs = System.currentTimeMillis() - timestamp
        val seconds = diffMs / 1000
        val minutes = seconds / 60
        val hours = minutes / 60

        return when {
            minutes < 1 -> "Just now"
            minutes == 1L -> "1 minute ago"
            minutes < 60 -> "$minutes minutes ago"
            hours == 1L -> "1 hour ago"
            hours < 24 -> "$hours hours ago"
            else -> {
                val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                sdf.format(Date(timestamp))
            }
        }
    }
}
