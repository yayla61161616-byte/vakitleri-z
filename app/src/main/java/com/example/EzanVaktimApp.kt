package com.example

import android.app.Application
import com.example.alarm.NotificationHelper
import com.example.alarm.PrayerAlarmScheduler
import com.example.alarm.SoundPlayerManager
import com.example.data.local.PrayerDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.remote.NetworkClient
import com.example.data.repository.PrayerTimesRepository

class AppContainer(application: Application) {
    private val database = PrayerDatabase.getInstance(application)
    val preferencesRepository = UserPreferencesRepository(application)
    val prayerTimesRepository = PrayerTimesRepository(
        prayerDao = database.prayerDao(),
        diyanetApi = NetworkClient.diyanetApi,
        aladhanApi = NetworkClient.aladhanApi
    )
    val notificationHelper = NotificationHelper(application)
    val soundPlayerManager = SoundPlayerManager(application)
    val alarmScheduler = PrayerAlarmScheduler(application)
}

class EzanVaktimApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notificationHelper.ensureChannels()
    }
}
