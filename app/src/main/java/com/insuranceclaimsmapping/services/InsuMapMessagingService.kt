package com.insuranceclaimsmapping.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.insuranceclaimsmapping.R
import com.insuranceclaimsmapping.activities.MainActivity
import com.insuranceclaimsmapping.firebase.FirebaseHelper
import com.insuranceclaimsmapping.utils.PrefManager

class InsuMapMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        getSharedPreferences("InsuMapPrefs", Context.MODE_PRIVATE).edit()
            .putString("fcm_token", token).apply()
        // Save token to Firestore so other users can send notifications to this device
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirebaseHelper().saveFcmToken(uid, token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        if (!PrefManager(this).getNotificationsEnabled()) return

        remoteMessage.notification?.let {
            sendNotification(it.title, it.body, remoteMessage.data["claimId"])
        } ?: run {
            if (remoteMessage.data.isNotEmpty()) {
                sendNotification(
                    remoteMessage.data["title"],
                    remoteMessage.data["body"],
                    remoteMessage.data["claimId"]
                )
            }
        }
    }

    private fun sendNotification(title: String?, messageBody: String?, claimId: String? = null) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            claimId?.let { putExtra("claimId", it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        )

        val channelId = "insumap_default_channel"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_medical_claim)
            .setContentTitle(title ?: "InsuMap Notification")
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(channelId, "InsuMap Alerts", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}
