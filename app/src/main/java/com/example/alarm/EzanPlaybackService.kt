package com.example.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.example.EzanVaktimApp
import com.example.domain.SoundType

class EzanPlaybackService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as EzanVaktimApp
        val container = app.container

        if (intent?.action == ACTION_STOP) {
            container.soundPlayerManager.stop()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
            stopSelf()
            return START_NOT_STICKY
        }

        val notificationId = intent?.getIntExtra(EXTRA_NOTIFICATION_ID, NotificationHelper.TEST_NOTIFICATION_ID)
            ?: NotificationHelper.TEST_NOTIFICATION_ID
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Namaz Vakti"
        val body = intent?.getStringExtra(EXTRA_BODY) ?: ""
        val soundType = SoundType.fromKey(intent?.getStringExtra(EXTRA_SOUND_TYPE))
        val channelId = if (soundType == SoundType.EZAN) {
            NotificationHelper.CHANNEL_EZAN_ID
        } else {
            NotificationHelper.CHANNEL_SHORT_ID
        }

        val notification = container.notificationHelper.buildPrayerNotification(
            title = title,
            body = body,
            channelId = channelId,
            includeStopAction = true
        )

        val fgType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }

        ServiceCompat.startForeground(this, notificationId, notification, fgType)

        container.soundPlayerManager.play(soundType) {
            // Detach foreground status but keep the prayer notification visible in shade
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
            stopSelf(startId)
        }

        return START_NOT_STICKY
    }

    companion object {
        private const val TAG = "EzanPlaybackService"
        const val ACTION_PLAY = "com.example.action.PLAY_PRAYER_SOUND"
        const val ACTION_STOP = "com.example.action.STOP_PRAYER_SOUND"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_BODY = "extra_body"
        const val EXTRA_SOUND_TYPE = "extra_sound_type"

        fun startOrFallback(
            context: Context,
            notificationId: Int,
            title: String,
            body: String,
            soundType: SoundType,
            notificationHelper: NotificationHelper,
            soundPlayerManager: SoundPlayerManager
        ) {
            val intent = Intent(context, EzanPlaybackService::class.java).apply {
                action = ACTION_PLAY
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_BODY, body)
                putExtra(EXTRA_SOUND_TYPE, soundType.key)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Foreground service restricted; using direct notification + player fallback", e)
                val channelId = if (soundType == SoundType.EZAN) {
                    NotificationHelper.CHANNEL_EZAN_ID
                } else {
                    NotificationHelper.CHANNEL_SHORT_ID
                }
                val notif = notificationHelper.buildPrayerNotification(
                    title = title,
                    body = body,
                    channelId = channelId,
                    includeStopAction = true
                )
                notificationHelper.postNotification(notificationId, notif)
                soundPlayerManager.play(soundType)
            }
        }

        fun stopPlayback(context: Context, soundPlayerManager: SoundPlayerManager) {
            soundPlayerManager.stop()
            runCatching {
                context.stopService(Intent(context, EzanPlaybackService::class.java))
            }
        }
    }
}
