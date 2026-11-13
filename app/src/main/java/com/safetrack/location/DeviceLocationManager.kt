package com.safetrack.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.safetrack.data.model.LocationData

class DeviceLocationManager(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var activeCallback: LocationCallback? = null
    private var lastEmittedLocation: Location? = null

    @SuppressLint("MissingPermission")
    fun getCurrentLocation(
        onSuccess: (LocationData) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (!LocationPermissionManager.hasAnyLocationPermission(context)) {
            onError(SecurityException("Location permission not granted"))
            return
        }

        if (!LocationPermissionManager.isLocationServiceEnabled(context)) {
            onError(IllegalStateException("GPS / Location services disabled on device"))
            return
        }

        val priority = if (LocationPermissionManager.hasFineLocationPermission(context)) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val cancellationTokenSource = CancellationTokenSource()

        fusedLocationClient.getCurrentLocation(priority, cancellationTokenSource.token)
            .addOnSuccessListener { location ->
                if (location != null) {
                    onSuccess(mapToLocationData(location))
                } else {
                    // Fall back to getLastLocation
                    fusedLocationClient.lastLocation
                        .addOnSuccessListener { lastLoc ->
                            if (lastLoc != null) {
                                onSuccess(mapToLocationData(lastLoc))
                            } else {
                                onError(Exception("Unable to acquire GPS fix. Please ensure location is enabled."))
                            }
                        }
                        .addOnFailureListener { e -> onError(e) }
                }
            }
            .addOnFailureListener { e -> onError(e) }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(
        intervalMs: Long,
        onLocationReceived: (LocationData) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (!LocationPermissionManager.hasAnyLocationPermission(context)) {
            onError(SecurityException("Location permission not granted"))
            return
        }

        if (!LocationPermissionManager.isLocationServiceEnabled(context)) {
            onError(IllegalStateException("Location services disabled"))
            return
        }

        stopLocationUpdates()

        val priority = if (LocationPermissionManager.hasFineLocationPermission(context)) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val minUpdateInterval = (intervalMs / 2).coerceAtLeast(5000L)

        val locationRequest = LocationRequest.Builder(priority, intervalMs)
            .setMinUpdateIntervalMillis(minUpdateInterval)
            .setMaxUpdateDelayMillis(intervalMs * 2)
            .setWaitForAccurateLocation(false)
            .build()

        activeCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return

                // Check for duplicate jitter within 2 meters if recent
                val prev = lastEmittedLocation
                if (prev != null &&
                    location.distanceTo(prev) < 2.0f &&
                    (location.time - prev.time) < 10_000L
                ) {
                    return // Skip redundant duplicate upload
                }

                lastEmittedLocation = location
                onLocationReceived(mapToLocationData(location))
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            activeCallback!!,
            Looper.getMainLooper()
        )
    }

    fun stopLocationUpdates() {
        activeCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            activeCallback = null
        }
    }

    private fun mapToLocationData(location: Location): LocationData {
        val isFine = LocationPermissionManager.hasFineLocationPermission(context)
        val now = System.currentTimeMillis()
        val fixTimestamp = if (location.time > 0L && location.time <= now) location.time else now

        return LocationData(
            userId = "", // Populated by caller
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy,
            speed = location.speed,
            altitude = location.altitude,
            timestamp = fixTimestamp,
            updatedAt = now,
            sharingStatus = LocationData.STATUS_ACTIVE,
            isOnline = true,
            isApproximate = !isFine,
            provider = location.provider ?: "fused"
        )
    }
}
