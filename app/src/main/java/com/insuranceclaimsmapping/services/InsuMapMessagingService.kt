package com.insuranceclaimsmapping.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.insuranceclaimsmapping.R
import com.insuranceclaimsmapping.activities.MainActivity
import com.insuranceclaimsmapping.utils.PrefManager

class InsuMapMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Optionally save the new token to Firestore here, or wait until login/app start
        val prefManager = PrefManager(this)
        // Store it locally for later use
        getSharedPreferences("InsuMapPrefs", Context.MODE_PRIVATE).edit()
            .putString("fcm_token", token).apply()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val prefManager = PrefManager(this)
        // Check if user has opted out of notifications
        if (!prefManager.getNotificationsEnabled()) {
            return
        }

        // Check if message contains a notification payload
        remoteMessage.notification?.let {
            sendNotification(it.title, it.body)
        } ?: run {
            // Check if message contains a data payload (background message)
            if (remoteMessage.data.isNotEmpty()) {
                val title = remoteMessage.data["title"]
                val body = remoteMessage.data["body"]
                sendNotification(title, body)
            }
        }
    }

    private fun sendNotification(title: String?, messageBody: String?) {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        )

        val channelId = "insumap_default_channel"
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            // Using ic_launcher as a placeholder. In production, use a white transparent icon for notifications.
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title ?: "InsuMap Notification")
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Since android Oreo notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "InsuMap Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}
