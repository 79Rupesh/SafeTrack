package com.safetrack.location

import android.content.Context
import androidx.lifecycle.LiveData
import com.safetrack.data.model.LocationData
import com.safetrack.database.dao.LocationDao
import com.safetrack.database.entity.LocationRecord
import com.safetrack.firebase.FirebaseRepository
import com.safetrack.utils.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocationRepository(
    private val locationDao: LocationDao,
    private val firebaseRepo: FirebaseRepository
) {

    fun getLocationHistory(userId: String): LiveData<List<LocationRecord>> {
        return locationDao.getLocationHistory(userId)
    }

    suspend fun recordAndUploadLocation(
        context: Context,
        location: LocationData
    ): Boolean = withContext(Dispatchers.IO) {
        // 1. Insert into local SQLite database via Room
        val record = LocationRecord(
            userId = location.userId,
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy,
            speed = location.speed,
            altitude = location.altitude,
            timestamp = location.timestamp,
            sharingStatus = location.sharingStatus,
            batteryPct = location.batteryPct,
            isSynced = false
        )
        val rowId = locationDao.insertLocation(record)

        // Enforce 48-hour local history retention policy to prevent database bloat
        try {
            val cutoff = System.currentTimeMillis() - (48 * 60 * 60 * 1000L)
            locationDao.deleteOlderThan(cutoff)
        } catch (e: Exception) {
            // Non-critical cleanup failure
        }

        // 2. Check internet connection
        val isOnline = NetworkUtils.isNetworkAvailable(context)
        if (isOnline) {
            val result = firebaseRepo.uploadLocation(location)
            if (result.isSuccess) {
                // Mark locally as synced
                locationDao.markAsSynced(listOf(rowId))
                // Also trigger sync for any prior unsynced records
                syncPendingLocationsInternal()
                return@withContext true
            }
        }
        return@withContext false
    }

    suspend fun syncPendingLocations(context: Context): Int = withContext(Dispatchers.IO) {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            return@withContext 0
        }
        return@withContext syncPendingLocationsInternal()
    }

    private suspend fun syncPendingLocationsInternal(): Int {
        val pending = locationDao.getPendingUnsyncedLocations()
        if (pending.isEmpty()) return 0

        val syncedIds = mutableListOf<Long>()
        for (item in pending) {
            val locationData = LocationData(
                userId = item.userId,
                latitude = item.latitude,
                longitude = item.longitude,
                accuracy = item.accuracy,
                speed = item.speed,
                altitude = item.altitude,
                timestamp = item.timestamp,
                sharingStatus = item.sharingStatus,
                batteryPct = item.batteryPct,
                isOnline = true
            )
            val result = firebaseRepo.uploadLocation(locationData)
            if (result.isSuccess) {
                syncedIds.add(item.id)
            } else {
                break // Stop on failure and retry later
            }
        }

        if (syncedIds.isNotEmpty()) {
            locationDao.markAsSynced(syncedIds)
        }
        return syncedIds.size
    }

    suspend fun getLastRecordedLocation(userId: String): LocationRecord? =
        withContext(Dispatchers.IO) {
            locationDao.getLastRecordedLocation(userId)
        }

    suspend fun clearHistory(userId: String) = withContext(Dispatchers.IO) {
        locationDao.clearHistory(userId)
    }
}
