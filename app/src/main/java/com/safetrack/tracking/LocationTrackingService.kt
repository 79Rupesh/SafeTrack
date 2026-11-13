package com.safetrack.tracking

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.safetrack.R
import com.safetrack.SafeTrackApp
import com.safetrack.dashboard.FriendDashboardActivity
import com.safetrack.data.model.LocationData
import com.safetrack.firebase.FirebaseRepository
import com.safetrack.location.LocationRepository
import com.safetrack.utils.NetworkUtils
import com.safetrack.utils.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LocationTrackingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var sessionManager: SessionManager
    private lateinit var locationRepository: LocationRepository
    private lateinit var firebaseRepo: FirebaseRepository
    private var wakeLock: PowerManager.WakeLock? = null

    private var isTracking = false
    private var isPaused = false
    private var lastUploadedLocation: Location? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as SafeTrackApp
        sessionManager = app.sessionManager
        firebaseRepo = FirebaseRepository()
        locationRepository = LocationRepository(app.database.locationDao(), firebaseRepo)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SafeTrack:LocationTrackingWakeLock"
            ).apply {
                setReferenceCounted(false)
            }
        } catch (e: Exception) {
            // PowerManager wake lock not available
        }

        setupLocationCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_STOP_SERVICE -> {
                stopLocationSharing()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE_SERVICE -> {
                pauseLocationSharing()
            }
            ACTION_RESUME_SERVICE -> {
                resumeLocationSharing()
            }
            else -> {
                startLocationSharingForeground()
            }
        }

        return START_STICKY
    }

    private fun startLocationSharingForeground() {
        val notification = buildServiceNotification(isPaused = false)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            sessionManager.isSharingActive = false
            stopSelf()
            return
        }

        sessionManager.isSharingActive = true
        sessionManager.isSharingPaused = false
        isTracking = true
        isPaused = false

        requestLocationUpdates()
        broadcastStatusChange(LocationData.STATUS_ACTIVE)
    }

    private fun pauseLocationSharing() {
        isPaused = true
        sessionManager.isSharingPaused = true
        fusedLocationClient.removeLocationUpdates(locationCallback)

        updateNotification(isPaused = true)
        broadcastStatusChange(LocationData.STATUS_PAUSED)

        val userId = sessionManager.userId ?: return
        serviceScope.launch {
            firebaseRepo.updateSharingStatus(userId, LocationData.STATUS_PAUSED)
        }
    }

    private fun resumeLocationSharing() {
        isPaused = false
        sessionManager.isSharingPaused = false
        updateNotification(isPaused = false)
        requestLocationUpdates()
        broadcastStatusChange(LocationData.STATUS_ACTIVE)

        val userId = sessionManager.userId ?: return
        serviceScope.launch {
            firebaseRepo.updateSharingStatus(userId, LocationData.STATUS_ACTIVE)
        }
    }

    private fun stopLocationSharing() {
        isTracking = false
        sessionManager.isSharingActive = false
        sessionManager.isSharingPaused = false
        fusedLocationClient.removeLocationUpdates(locationCallback)

        broadcastStatusChange(LocationData.STATUS_STOPPED)

        val userId = sessionManager.userId ?: return
        serviceScope.launch {
            firebaseRepo.updateSharingStatus(userId, LocationData.STATUS_STOPPED)
            firebaseRepo.clearRemoteHistory(userId)
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates() {
        val intervalMs = sessionManager.updateIntervalMs
        val minIntervalMs = sessionManager.minUpdateIntervalMs
        val minDistanceMeters = sessionManager.minDistanceChangeMeters

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(minIntervalMs)
            .setMinUpdateDistanceMeters(minDistanceMeters)
            .setMaxUpdateDelayMillis(intervalMs * 2)
            .setWaitForAccurateLocation(false)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            // Permission revoked
            stopSelf()
        }
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                onNewLocation(location)
            }
        }
    }

    private fun onNewLocation(location: Location) {
        val userId = sessionManager.userId ?: return

        // Jitter rejection: drop stationary micro-drift (< 2m and under min interval)
        val lastLoc = lastUploadedLocation
        if (lastLoc != null) {
            val dist = location.distanceTo(lastLoc)
            val timeElapsed = location.time - lastLoc.time
            if (dist < 2.0f && timeElapsed < sessionManager.minUpdateIntervalMs) {
                return
            }
        }
        lastUploadedLocation = location

        val batteryPct = getBatteryPercentage()

        // Cache in session
        sessionManager.lastLatitude = location.latitude.toFloat()
        sessionManager.lastLongitude = location.longitude.toFloat()
        sessionManager.lastUpdateTime = location.time
        sessionManager.lastAccuracy = location.accuracy

        val isOnline = NetworkUtils.isNetworkAvailable(applicationContext)

        val locationData = LocationData(
            userId = userId,
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy,
            speed = location.speed,
            altitude = location.altitude,
            timestamp = location.time,
            updatedAt = System.currentTimeMillis(),
            sharingStatus = if (isPaused) LocationData.STATUS_PAUSED else LocationData.STATUS_ACTIVE,
            batteryPct = batteryPct,
            isOnline = isOnline,
            isApproximate = false,
            provider = location.provider ?: "fused"
        )

        try {
            wakeLock?.acquire(3000L)
        } catch (e: Exception) {
            // Ignore
        }

        // Save locally in Room and push to Firebase Realtime Database
        serviceScope.launch {
            val uploadSuccess = locationRepository.recordAndUploadLocation(applicationContext, locationData)
            if (uploadSuccess) {
                sessionManager.lastUploadTime = System.currentTimeMillis()
            }

            // Broadcast to live active UI components
            val broadcastIntent = Intent(ACTION_LOCATION_BROADCAST).apply {
                putExtra(EXTRA_LATITUDE, location.latitude)
                putExtra(EXTRA_LONGITUDE, location.longitude)
                putExtra(EXTRA_ACCURACY, location.accuracy)
                putExtra(EXTRA_TIMESTAMP, location.time)
                putExtra(EXTRA_UPLOAD_SUCCESS, uploadSuccess)
                putExtra(EXTRA_LAST_UPLOAD_TIME, sessionManager.lastUploadTime)
                putExtra(EXTRA_IS_ONLINE, isOnline)
            }
            sendBroadcast(broadcastIntent)
        }
    }

    private fun buildServiceNotification(isPaused: Boolean): Notification {
        val openAppIntent = Intent(this, FriendDashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ServiceStopActionReceiver::class.java).apply {
            action = ACTION_STOP_SHARING
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeIntent = Intent(this, ServiceStopActionReceiver::class.java).apply {
            action = if (isPaused) ACTION_RESUME_SHARING else ACTION_PAUSE_SHARING
        }
        val pauseResumePendingIntent = PendingIntent.getBroadcast(
            this,
            2,
            pauseResumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isPaused) {
            getString(R.string.notification_title_paused)
        } else {
            getString(R.string.notification_title_active)
        }

        val body = if (isPaused) {
            getString(R.string.notification_body_paused)
        } else {
            getString(R.string.notification_body_active)
        }

        val pauseResumeActionText = if (isPaused) {
            getString(R.string.notification_action_resume)
        } else {
            getString(R.string.notification_action_pause)
        }

        return NotificationCompat.Builder(this, SafeTrackApp.CHANNEL_LOCATION_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_location)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(
                if (isPaused) R.drawable.ic_refresh else R.drawable.ic_close,
                pauseResumeActionText,
                pauseResumePendingIntent
            )
            .addAction(
                R.drawable.ic_close,
                getString(R.string.notification_action_stop),
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(isPaused: Boolean) {
        val notification = buildServiceNotification(isPaused)
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun broadcastStatusChange(status: String) {
        val intent = Intent(ACTION_STATUS_BROADCAST).apply {
            putExtra(EXTRA_SHARING_STATUS, status)
        }
        sendBroadcast(intent)
    }

    private fun getBatteryPercentage(): Int {
        val batteryStatus: Intent? = registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) {
            (level * 100 / scale)
        } else {
            -1
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            // Ignore
        }
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            // Ignore
        }
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_SERVICE = "com.safetrack.ACTION_START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.safetrack.ACTION_STOP_SERVICE"
        const val ACTION_PAUSE_SERVICE = "com.safetrack.ACTION_PAUSE_SERVICE"
        const val ACTION_RESUME_SERVICE = "com.safetrack.ACTION_RESUME_SERVICE"

        const val ACTION_STOP_SHARING = "com.safetrack.ACTION_STOP_SHARING"
        const val ACTION_PAUSE_SHARING = "com.safetrack.ACTION_PAUSE_SHARING"
        const val ACTION_RESUME_SHARING = "com.safetrack.ACTION_RESUME_SHARING"

        const val ACTION_LOCATION_BROADCAST = "com.safetrack.ACTION_LOCATION_BROADCAST"
        const val ACTION_STATUS_BROADCAST = "com.safetrack.ACTION_STATUS_BROADCAST"

        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"
        const val EXTRA_ACCURACY = "extra_accuracy"
        const val EXTRA_TIMESTAMP = "extra_timestamp"
        const val EXTRA_SHARING_STATUS = "extra_sharing_status"
        const val EXTRA_UPLOAD_SUCCESS = "extra_upload_success"
        const val EXTRA_LAST_UPLOAD_TIME = "extra_last_upload_time"
        const val EXTRA_IS_ONLINE = "extra_is_online"

        fun startService(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }

        fun pauseService(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_PAUSE_SERVICE
            }
            context.startService(intent)
        }

        fun resumeService(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_RESUME_SERVICE
            }
            context.startService(intent)
        }
    }
}
