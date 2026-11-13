package com.safetrack

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase
import com.safetrack.database.SafeTrackDatabase
import com.safetrack.utils.SessionManager

class SafeTrackApp : Application() {

    lateinit var sessionManager: SessionManager
        private set

    lateinit var database: SafeTrackDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        try {
            // Enable offline disk cache for Firebase Realtime Database
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        } catch (e: Exception) {
            // May fail if already initialized or in certain test configurations
        }

        sessionManager = SessionManager(this)
        database = SafeTrackDatabase.getDatabase(this)

        createNotificationChannels()

        // Schedule WorkManager periodic offline location sync
        try {
            com.safetrack.location.LocationSyncWorker.schedulePeriodicSync(this)
        } catch (e: Exception) {
            // Non-critical scheduling failure
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Location sharing channel (Low or Default importance so it doesn't chime continuously)
            val locationChannel = NotificationChannel(
                CHANNEL_LOCATION_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }

            // SOS alert channel (High importance with sound and vibration)
            val sosChannel = NotificationChannel(
                CHANNEL_SOS_ID,
                getString(R.string.notification_sos_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical SOS Emergency Alerts"
                enableVibration(true)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(locationChannel)
            notificationManager.createNotificationChannel(sosChannel)
        }
    }

    companion object {
        const val CHANNEL_LOCATION_ID = "safetrack_location_channel"
        const val CHANNEL_SOS_ID = "safetrack_sos_channel"

        lateinit var instance: SafeTrackApp
            private set
    }
}
