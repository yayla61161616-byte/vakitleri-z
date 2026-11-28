package com.example.alarm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.domain.PrayerDay
import com.example.domain.PrayerTimeCalculator
import com.example.domain.SelectedLocation

class NotificationHelper(private val context: Context) {

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val ezanChannel = NotificationChannel(
            CHANNEL_EZAN_ID,
            context.getString(R.string.notif_channel_ezan_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_ezan_desc)
            setSound(null, null)
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val shortChannel = NotificationChannel(
            CHANNEL_SHORT_ID,
            context.getString(R.string.notif_channel_short_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_short_desc)
            setSound(null, null)
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val countdownChannel = NotificationChannel(
            CHANNEL_COUNTDOWN_ID,
            context.getString(R.string.notif_channel_countdown_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notif_channel_countdown_desc)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        manager.createNotificationChannel(ezanChannel)
        manager.createNotificationChannel(shortChannel)
        manager.createNotificationChannel(countdownChannel)
    }

    fun updateCountdownNotification(
        days: List<PrayerDay>,
        location: SelectedLocation,
        enabled: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        if (!enabled || !hasNotificationPermission()) {
            cancelCountdownNotification()
            return
        }

        val todayIso = PrayerTimeCalculator.todayIsoDate(nowMillis)
        val tomorrowIso = PrayerTimeCalculator.tomorrowIsoDate(nowMillis)
        val today = days.firstOrNull { it.isoDate == todayIso } ?: days.firstOrNull() ?: return
        val tomorrow = days.firstOrNull { it.isoDate == tomorrowIso }

        val status = PrayerTimeCalculator.computeStatus(today, tomorrow, nowMillis) ?: return
        ensureChannels()

        val remainingFormatted = PrayerTimeCalculator.formatHoursMinutesTurkish(status.remainingMillis)
        val title = "${status.nextPrayer.displayName} • ${status.nextPrayerTimeText} (${location.formattedDisplay()})"
        val body = "${status.nextPrayer.displayName} vaktine $remainingFormatted kaldı"

        val contentIntent = PendingIntent.getActivity(
            context,
            10,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_COUNTDOWN_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setSubText(location.formattedDisplay())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(true)
            .setWhen(status.nextPrayerTriggerMillis)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent)
            .build()

        postNotification(COUNTDOWN_NOTIFICATION_ID, notification)
    }

    fun cancelCountdownNotification() {
        NotificationManagerCompat.from(context).cancel(COUNTDOWN_NOTIFICATION_ID)
    }

    fun buildPrayerNotification(
        title: String,
        body: String,
        channelId: String,
        includeStopAction: Boolean
    ): Notification {
        ensureChannels()

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)

        if (includeStopAction) {
            val stopIntent = Intent(context, EzanPlaybackService::class.java).apply {
                action = EzanPlaybackService.ACTION_STOP
            }
            val stopPendingIntent = PendingIntent.getService(
                context,
                999,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                android.R.drawable.ic_media_pause,
                context.getString(R.string.action_stop_sound),
                stopPendingIntent
            )
        }

        return builder.build()
    }

    fun postNotification(notificationId: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    companion object {
        const val CHANNEL_EZAN_ID = "prayer_alarm_ezan_channel"
        const val CHANNEL_SHORT_ID = "prayer_alarm_short_channel"
        const val CHANNEL_COUNTDOWN_ID = "prayer_countdown_bar_channel"
        const val COUNTDOWN_NOTIFICATION_ID = 1000
        const val TEST_NOTIFICATION_ID = 1099
    }
}
