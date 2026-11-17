package com.example.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.data.model.MedicationLog

class ReminderScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleReminder(log: MedicationLog, instructions: String) {
        val intent = Intent(context, ReminderNotificationReceiver::class.java).apply {
            putExtra("MED_NAME", log.medicationName)
            putExtra("DOSAGE", log.dosage)
            putExtra("INSTRUCTIONS", instructions)
            putExtra("LOG_ID", log.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            log.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMs = log.scheduledTimeMs
        if (triggerAtMs > System.currentTimeMillis()) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMs,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMs,
                        pendingIntent
                    )
                }
            } catch (e: SecurityException) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            }
        }
    }
}
