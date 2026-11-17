package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.R

class ReminderNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val medName = intent.getStringExtra("MED_NAME") ?: "Médicament"
        val dosage = intent.getStringExtra("DOSAGE") ?: ""
        val instructions = intent.getStringExtra("INSTRUCTIONS") ?: ""
        val logId = intent.getLongExtra("LOG_ID", 0L)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channelId = "medisnap_reminders"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Rappels de médicaments MediSnap",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications de rappel intelligentes pour vos prises de médicaments"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val contentText = if (instructions.isNotBlank()) {
            "$dosage — $instructions"
        } else {
            "C'est l'heure de prendre votre traitement : $dosage"
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Rappel MediSnap : $medName")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notificationManager.notify(logId.toInt().coerceAtLeast(1001), builder.build())
    }
}
