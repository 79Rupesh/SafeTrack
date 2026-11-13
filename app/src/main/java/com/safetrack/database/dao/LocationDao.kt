package com.safetrack.database.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.safetrack.database.entity.LocationRecord

@Dao
interface LocationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(record: LocationRecord): Long

    @Query("SELECT * FROM location_records WHERE isSynced = 0 ORDER BY timestamp ASC")
    suspend fun getPendingUnsyncedLocations(): List<LocationRecord>

    @Query("UPDATE location_records SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)

    @Query("SELECT * FROM location_records WHERE userId = :userId ORDER BY timestamp DESC LIMIT 100")
    fun getLocationHistory(userId: String): LiveData<List<LocationRecord>>

    @Query("SELECT * FROM location_records WHERE userId = :userId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastRecordedLocation(userId: String): LocationRecord?

    @Query("DELETE FROM location_records WHERE userId = :userId")
    suspend fun clearHistory(userId: String)

    @Query("DELETE FROM location_records WHERE timestamp < :olderThanTimestamp")
    suspend fun deleteOlderThan(olderThanTimestamp: Long)
}
