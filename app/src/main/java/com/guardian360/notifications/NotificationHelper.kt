package com.guardian360.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.R

class NotificationHelper(private val context: Context) {

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            val channels = listOf(
                NotificationChannel(CHANNEL_APPROVALS, "Approvals & Requests", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CHANNEL_SECURITY, "Security & Tamper Alerts", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CHANNEL_SAFETY, "Screen Time & Location", NotificationManager.IMPORTANCE_DEFAULT)
            )
            channels.forEach { nm.createNotificationChannel(it) }
        }
    }

    fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return runCatching {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    fun showLocalAlert(channelId: String, notificationId: Int, title: String, body: String) {
        if (!hasNotificationPermission()) return
        ensureChannels()
        runCatching {
            val notif = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(notificationId, notif)
        }
    }

    companion object {
        const val CHANNEL_APPROVALS = "guardian360_approvals"
        const val CHANNEL_SECURITY = "guardian360_security"
        const val CHANNEL_SAFETY = "guardian360_safety"
    }
}
