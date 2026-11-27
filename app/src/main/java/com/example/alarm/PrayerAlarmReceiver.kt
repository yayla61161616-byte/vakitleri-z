package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.example.EzanVaktimApp
import com.example.R
import com.example.domain.PrayerType
import com.example.domain.SoundType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PrayerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PRAYER_ALARM) return

        val app = context.applicationContext as? EzanVaktimApp ?: return
        val container = app.container

        val prayerKey = intent.getStringExtra(EXTRA_PRAYER_KEY)
        val prayerType = PrayerType.fromKey(prayerKey) ?: return
        val prayerTime = intent.getStringExtra(EXTRA_PRAYER_TIME) ?: ""
        val locationLabel = intent.getStringExtra(EXTRA_LOCATION_LABEL) ?: ""
        val fallbackSound = SoundType.fromKey(intent.getStringExtra(EXTRA_SOUND_TYPE))

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "EzanVaktim:PrayerAlarmWakeLock"
        ).apply { acquire(10_000L) }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = container.preferencesRepository.getNotificationPreferences()
                val location = container.preferencesRepository.getSelectedLocation()

                if (prefs.isEnabled(prayerType)) {
                    val soundType = prefs.soundType
                    val title = context.getString(R.string.notif_prayer_entered_title, prayerType.displayName)
                    val placeText = locationLabel.ifBlank { location.formattedDisplay() }
                    val body = if (prayerTime.isNotBlank()) {
                        "$placeText • $prayerTime — ${prayerType.displayName} vakti girdi."
                    } else {
                        context.getString(R.string.notif_prayer_entered_body, prayerType.displayName, placeText)
                    }

                    EzanPlaybackService.startOrFallback(
                        context = context.applicationContext,
                        notificationId = prayerType.notificationId,
                        title = title,
                        body = body,
                        soundType = soundType,
                        notificationHelper = container.notificationHelper,
                        soundPlayerManager = container.soundPlayerManager
                    )
                }

                // Keep upcoming alarms populated for subsequent days
                val days = container.prayerTimesRepository.getUpcomingPrayerDays(location.districtId, 7)
                container.alarmScheduler.rescheduleAll(days, prefs, location)
            } catch (_: Exception) {
                val title = context.getString(R.string.notif_prayer_entered_title, prayerType.displayName)
                val body = context.getString(R.string.notif_prayer_entered_body, prayerType.displayName, locationLabel)
                EzanPlaybackService.startOrFallback(
                    context = context.applicationContext,
                    notificationId = prayerType.notificationId,
                    title = title,
                    body = body,
                    soundType = fallbackSound,
                    notificationHelper = container.notificationHelper,
                    soundPlayerManager = container.soundPlayerManager
                )
            } finally {
                if (wakeLock.isHeld) {
                    runCatching { wakeLock.release() }
                }
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_PRAYER_ALARM = "com.example.action.PRAYER_ALARM"
        const val EXTRA_PRAYER_KEY = "extra_prayer_key"
        const val EXTRA_PRAYER_TIME = "extra_prayer_time"
        const val EXTRA_LOCATION_LABEL = "extra_location_label"
        const val EXTRA_SOUND_TYPE = "extra_sound_type"
    }
}
