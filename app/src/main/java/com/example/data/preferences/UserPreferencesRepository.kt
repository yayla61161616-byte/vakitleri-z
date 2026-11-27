package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.NotificationPreferences
import com.example.domain.PrayerType
import com.example.domain.SelectedLocation
import com.example.domain.SoundType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.prayerPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ezan_vaktim_prefs"
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val COUNTRY_ID = stringPreferencesKey("country_id")
        val COUNTRY_NAME = stringPreferencesKey("country_name")
        val CITY_ID = stringPreferencesKey("city_id")
        val CITY_NAME = stringPreferencesKey("city_name")
        val DISTRICT_ID = stringPreferencesKey("district_id")
        val DISTRICT_NAME = stringPreferencesKey("district_name")
        val SOUND_TYPE = stringPreferencesKey("sound_type")

        fun prayerEnabled(type: PrayerType) = booleanPreferencesKey("prayer_enabled_${type.key}")
    }

    val selectedLocationFlow: Flow<SelectedLocation> = context.prayerPrefsDataStore.data.map { prefs ->
        val defaultLoc = SelectedLocation()
        SelectedLocation(
            countryId = prefs[Keys.COUNTRY_ID] ?: defaultLoc.countryId,
            countryName = prefs[Keys.COUNTRY_NAME] ?: defaultLoc.countryName,
            cityId = prefs[Keys.CITY_ID] ?: defaultLoc.cityId,
            cityName = prefs[Keys.CITY_NAME] ?: defaultLoc.cityName,
            districtId = prefs[Keys.DISTRICT_ID] ?: defaultLoc.districtId,
            districtName = prefs[Keys.DISTRICT_NAME] ?: defaultLoc.districtName
        )
    }

    val notificationPreferencesFlow: Flow<NotificationPreferences> =
        context.prayerPrefsDataStore.data.map { prefs ->
            val soundType = SoundType.fromKey(prefs[Keys.SOUND_TYPE])
            val enabledMap = PrayerType.entries.associateWith { type ->
                prefs[Keys.prayerEnabled(type)] ?: true
            }
            NotificationPreferences(
                soundType = soundType,
                enabledPrayers = enabledMap
            )
        }

    suspend fun getSelectedLocation(): SelectedLocation = selectedLocationFlow.first()

    suspend fun getNotificationPreferences(): NotificationPreferences = notificationPreferencesFlow.first()

    suspend fun saveSelectedLocation(location: SelectedLocation) {
        context.prayerPrefsDataStore.edit { prefs ->
            prefs[Keys.COUNTRY_ID] = location.countryId
            prefs[Keys.COUNTRY_NAME] = location.countryName
            prefs[Keys.CITY_ID] = location.cityId
            prefs[Keys.CITY_NAME] = location.cityName
            prefs[Keys.DISTRICT_ID] = location.districtId
            prefs[Keys.DISTRICT_NAME] = location.districtName
        }
    }

    suspend fun setPrayerEnabled(type: PrayerType, enabled: Boolean) {
        context.prayerPrefsDataStore.edit { prefs ->
            prefs[Keys.prayerEnabled(type)] = enabled
        }
    }

    suspend fun setSoundType(soundType: SoundType) {
        context.prayerPrefsDataStore.edit { prefs ->
            prefs[Keys.SOUND_TYPE] = soundType.key
        }
    }
}
