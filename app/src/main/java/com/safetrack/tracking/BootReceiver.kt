package com.safetrack.tracking

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.safetrack.R
import com.safetrack.SafeTrackApp
import com.safetrack.dashboard.FriendDashboardActivity
import com.safetrack.location.LocationPermissionManager
import com.safetrack.utils.SessionManager

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val sessionManager = SessionManager(context)

            // 1. Verify user was logged in and sharing was actively enabled prior to reboot
            if (!sessionManager.isLoggedIn ||
                !sessionManager.isSharingActive ||
                sessionManager.isSharingPaused
            ) {
                return
            }

            // 2. Verify location permissions are still granted
            if (!LocationPermissionManager.hasAnyLocationPermission(context)) {
                notifyUserToReopen(context, "Location permission required to resume safety sharing.")
                return
            }

            // 3. Attempt to start Foreground Service adhering to Android 12+ (API 31+) background restrictions
            var startedSuccessfully = false
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // On Android 12+, background starts of foreground services are restricted
                    // unless the app is in an exempted state.
                    LocationTrackingService.startService(context)
                    startedSuccessfully = true
                } else {
                    LocationTrackingService.startService(context)
                    startedSuccessfully = true
                }
            } catch (e: Exception) {
                // ForegroundServiceStartNotAllowedException or SecurityException
                startedSuccessfully = false
            }

            // 4. Fallback: If foreground service could not be started directly from background,
            // post a high-priority prompt notification alerting the user to tap and resume sharing
            if (!startedSuccessfully) {
                notifyUserToReopen(
                    context,
                    context.getString(R.string.notification_boot_body)
                )
            }
        }
    }

    private fun notifyUserToReopen(context: Context, message: String) {
        val openIntent = Intent(context, FriendDashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            101,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, SafeTrackApp.CHANNEL_LOCATION_ID)
            .setSmallIcon(R.drawable.ic_location)
            .setContentTitle(context.getString(R.string.notification_boot_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_BOOT_ID, notification)
    }

    companion object {
        const val NOTIFICATION_BOOT_ID = 1002
    }
}
