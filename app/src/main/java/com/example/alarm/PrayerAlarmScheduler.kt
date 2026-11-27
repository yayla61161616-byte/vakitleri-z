package com.example.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.domain.NotificationPreferences
import com.example.domain.PrayerDay
import com.example.domain.PrayerTimeCalculator
import com.example.domain.PrayerType
import com.example.domain.SelectedLocation

class PrayerAlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun rescheduleAll(
        days: List<PrayerDay>,
        preferences: NotificationPreferences,
        location: SelectedLocation,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        cancelAllScheduled()
        if (days.isEmpty()) return

        val locationDisplay = location.formattedDisplay()
        val upcomingDays = days.take(MAX_DAYS_TO_SCHEDULE)

        upcomingDays.forEachIndexed { dayOffset, day ->
            PrayerType.entries.forEach { prayerType ->
                if (!preferences.isEnabled(prayerType)) return@forEach

                val timeStr = day.timeFor(prayerType).take(5)
                val triggerAtMillis = PrayerTimeCalculator.parseTimeToMillis(day.isoDate, timeStr)
                    ?: return@forEach

                if (triggerAtMillis <= nowMillis + 1_000L) return@forEach

                val requestCode = prayerType.alarmBaseCode + dayOffset
                val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
                    action = PrayerAlarmReceiver.ACTION_PRAYER_ALARM
                    putExtra(PrayerAlarmReceiver.EXTRA_PRAYER_KEY, prayerType.key)
                    putExtra(PrayerAlarmReceiver.EXTRA_PRAYER_TIME, timeStr)
                    putExtra(PrayerAlarmReceiver.EXTRA_LOCATION_LABEL, locationDisplay)
                    putExtra(PrayerAlarmReceiver.EXTRA_SOUND_TYPE, preferences.soundType.key)
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                scheduleSingleAlarm(triggerAtMillis, pendingIntent)
            }
        }
    }

    private fun scheduleSingleAlarm(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        try {
            if (canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (se: SecurityException) {
            Log.w(TAG, "Exact alarm permission missing, falling back to inexact allowWhileIdle", se)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    fun cancelAllScheduled() {
        for (dayOffset in 0 until MAX_DAYS_TO_SCHEDULE) {
            PrayerType.entries.forEach { prayerType ->
                val requestCode = prayerType.alarmBaseCode + dayOffset
                val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
                    action = PrayerAlarmReceiver.ACTION_PRAYER_ALARM
                }
                val existing = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (existing != null) {
                    alarmManager.cancel(existing)
                    existing.cancel()
                }
            }
        }
    }

    companion object {
        private const val TAG = "PrayerAlarmScheduler"
        private const val MAX_DAYS_TO_SCHEDULE = 7
    }
}
