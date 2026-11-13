package com.safetrack.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.safetrack.database.dao.LocationDao
import com.safetrack.database.entity.LocationRecord

@Database(entities = [LocationRecord::class], version = 1, exportSchema = false)
abstract class SafeTrackDatabase : RoomDatabase() {

    abstract fun locationDao(): LocationDao

    companion object {
        @Volatile
        private var INSTANCE: SafeTrackDatabase? = null

        fun getDatabase(context: Context): SafeTrackDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SafeTrackDatabase::class.java,
                    "safetrack_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
