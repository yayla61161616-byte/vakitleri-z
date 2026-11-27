package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.EzanVaktimApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (
            action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_TIMEZONE_CHANGED
        ) return

        val app = context.applicationContext as? EzanVaktimApp ?: return
        val container = app.container

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val location = container.preferencesRepository.getSelectedLocation()
                val prefs = container.preferencesRepository.getNotificationPreferences()
                val days = container.prayerTimesRepository.getUpcomingPrayerDays(location.districtId, 7)
                container.alarmScheduler.rescheduleAll(days, prefs, location)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
