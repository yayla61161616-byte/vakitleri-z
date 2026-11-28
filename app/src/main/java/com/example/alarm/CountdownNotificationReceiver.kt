package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.EzanVaktimApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CountdownNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REFRESH_COUNTDOWN) return

        val app = context.applicationContext as? EzanVaktimApp ?: return
        val container = app.container

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = container.preferencesRepository.getNotificationPreferences()
                val location = container.preferencesRepository.getSelectedLocation()
                val days = container.prayerTimesRepository.getUpcomingPrayerDays(location.districtId, 3)
                container.notificationHelper.updateCountdownNotification(
                    days = days,
                    location = location,
                    enabled = prefs.persistentCountdownEnabled
                )
                if (prefs.persistentCountdownEnabled) {
                    container.alarmScheduler.scheduleNextCountdownRefresh()
                } else {
                    container.alarmScheduler.cancelCountdownRefresh()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_REFRESH_COUNTDOWN = "com.example.action.REFRESH_COUNTDOWN"
    }
}
