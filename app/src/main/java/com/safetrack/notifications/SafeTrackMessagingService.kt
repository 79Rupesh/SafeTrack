package com.safetrack.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.safetrack.R
import com.safetrack.SafeTrackApp
import com.safetrack.contacts.ConnectionRequestsActivity
import com.safetrack.maps.GoogleMapsActivity

class SafeTrackMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val type = remoteMessage.data["type"]
        val senderId = remoteMessage.data["senderId"]
        val senderName = remoteMessage.data["senderName"] ?: "A contact"
        val message = remoteMessage.data["message"] ?: remoteMessage.notification?.body ?: ""

        when (type) {
            "SOS" -> {
                showSosNotification(senderId, senderName, message)
            }
            "CONNECTION_REQUEST" -> {
                showConnectionNotification(
                    "New Connection Request",
                    "$senderName wants to connect with you on SafeTrack."
                )
            }
            "REQUEST_ACCEPTED" -> {
                showConnectionNotification(
                    "Connection Request Accepted",
                    "$senderName accepted your SafeTrack connection request."
                )
            }
            "REQUEST_REJECTED" -> {
                showConnectionNotification(
                    "Connection Request Rejected",
                    "$senderName declined your connection request."
                )
            }
            "CONNECTION_REVOKED" -> {
                showConnectionNotification(
                    "Connection Revoked",
                    "A trusted contact disconnected their SafeTrack pairing with you."
                )
            }
            else -> {
                showGeneralNotification(
                    remoteMessage.notification?.title ?: "SafeTrack Alert",
                    message
                )
            }
        }
    }

    private fun showSosNotification(senderId: String?, senderName: String, message: String) {
        val intent = Intent(this, GoogleMapsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(GoogleMapsActivity.EXTRA_FRIEND_USER_ID, senderId)
            putExtra(GoogleMapsActivity.EXTRA_FRIEND_NAME, senderName)
            putExtra(GoogleMapsActivity.EXTRA_IS_SOS, true)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            101,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, SafeTrackApp.CHANNEL_SOS_ID)
            .setContentTitle("EMERGENCY SOS: $senderName")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_sos)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .build()

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(101, notification)
    }

    private fun showConnectionNotification(title: String, message: String) {
        val intent = Intent(this, ConnectionRequestsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            102,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, SafeTrackApp.CHANNEL_LOCATION_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_shield)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify((System.currentTimeMillis() % 10000).toInt(), notification)
    }

    private fun showGeneralNotification(title: String, message: String) {
        val notification = NotificationCompat.Builder(this, SafeTrackApp.CHANNEL_LOCATION_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_shield)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify((System.currentTimeMillis() % 10000).toInt(), notification)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
    }
}
