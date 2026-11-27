package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.R
import com.example.alarm.EzanPlaybackService
import com.example.alarm.NotificationHelper
import com.example.domain.CityOption
import com.example.domain.CountryOption
import com.example.domain.DistrictOption
import com.example.domain.NotificationPreferences
import com.example.domain.PrayerCountdownStatus
import com.example.domain.PrayerDay
import com.example.domain.PrayerTimeCalculator
import com.example.domain.PrayerType
import com.example.domain.SelectedLocation
import com.example.domain.SoundType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val location: SelectedLocation = SelectedLocation(),
    val today: PrayerDay? = null,
    val tomorrow: PrayerDay? = null,
    val countdown: PrayerCountdownStatus? = null,
    val preferences: NotificationPreferences = NotificationPreferences(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val usedCacheFallback: Boolean = false,
    val statusMessage: String? = null,
    val isSoundPlaying: Boolean = false
)

data class PermissionStatusUi(
    val hasNotificationPermission: Boolean = true,
    val canScheduleExactAlarms: Boolean = true
)

enum class LocationStep { COUNTRY, CITY, DISTRICT }

data class LocationPickerUiState(
    val step: LocationStep = LocationStep.COUNTRY,
    val searchQuery: String = "",
    val countries: List<CountryOption> = emptyList(),
    val cities: List<CityOption> = emptyList(),
    val districts: List<DistrictOption> = emptyList(),
    val pendingCountry: CountryOption? = null,
    val pendingCity: CityOption? = null,
    val isLoadingList: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class PrayerViewModel(
    private val appContext: Context,
    private val container: AppContainer
) : ViewModel() {

    private val syncState = MutableStateFlow(SyncBannerState())
    private val permissionState = MutableStateFlow(readPermissions())

    private val tickerFlow = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000L)
        }
    }

    private val selectedLocationFlow = container.preferencesRepository.selectedLocationFlow
    private val preferencesFlow = container.preferencesRepository.notificationPreferencesFlow

    private val upcomingDaysFlow = selectedLocationFlow
        .flatMapLatest { loc ->
            container.prayerTimesRepository.observeUpcomingPrayerDays(loc.districtId)
        }

    val homeUiState: StateFlow<HomeUiState> = combine(
        selectedLocationFlow,
        upcomingDaysFlow,
        preferencesFlow,
        tickerFlow,
        syncState,
        container.soundPlayerManager.isPlaying
    ) { values ->
        val location = values[0] as SelectedLocation
        @Suppress("UNCHECKED_CAST")
        val days = values[1] as List<PrayerDay>
        val prefs = values[2] as NotificationPreferences
        val nowMillis = values[3] as Long
        val sync = values[4] as SyncBannerState
        val isPlaying = values[5] as Boolean

        val todayIso = PrayerTimeCalculator.todayIsoDate(nowMillis)
        val tomorrowIso = PrayerTimeCalculator.tomorrowIsoDate(nowMillis)
        val today = days.firstOrNull { it.isoDate == todayIso } ?: days.firstOrNull()
        val tomorrow = days.firstOrNull { it.isoDate == tomorrowIso }
        val countdown = today?.let { PrayerTimeCalculator.computeStatus(it, tomorrow, nowMillis) }

        HomeUiState(
            location = location,
            today = today,
            tomorrow = tomorrow,
            countdown = countdown,
            preferences = prefs,
            isLoading = today == null && sync.isRefreshing,
            isRefreshing = sync.isRefreshing,
            usedCacheFallback = sync.usedCacheFallback,
            statusMessage = sync.message,
            isSoundPlaying = isPlaying
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    val permissionsUiState: StateFlow<PermissionStatusUi> = permissionState.asStateFlow()
    val isSoundPlaying: StateFlow<Boolean> = container.soundPlayerManager.isPlaying
    val preferencesState: StateFlow<NotificationPreferences> = preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NotificationPreferences()
    )
    val hasCustomEzanFile: Boolean = container.soundPlayerManager.hasCustomEzanFile()

    private val pickerState = MutableStateFlow(LocationPickerUiState())
    private val selectedCountryIdForPicker = MutableStateFlow("2")
    private val selectedCityIdForPicker = MutableStateFlow("539")

    val locationPickerUiState: StateFlow<LocationPickerUiState> = combine(
        pickerState,
        container.prayerTimesRepository.observeCountries(),
        selectedCountryIdForPicker.flatMapLatest { container.prayerTimesRepository.observeCities(it) },
        selectedCityIdForPicker.flatMapLatest { container.prayerTimesRepository.observeDistricts(it) }
    ) { state, countries, cities, districts ->
        state.copy(
            countries = countries,
            cities = cities,
            districts = districts
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LocationPickerUiState()
    )

    init {
        viewModelScope.launch {
            container.prayerTimesRepository.ensureLocationSeedsLoaded()
        }

        // Refresh prayer times whenever selected location changes
        viewModelScope.launch {
            selectedLocationFlow
                .distinctUntilChanged()
                .collect { location ->
                    refreshPrayerTimesFor(location)
                }
        }

        // Reschedule AlarmManager alarms whenever cached days, preferences, or location change
        viewModelScope.launch {
            combine(upcomingDaysFlow, preferencesFlow, selectedLocationFlow) { days, prefs, loc ->
                Triple(days, prefs, loc)
            }.collect { (days, prefs, loc) ->
                container.alarmScheduler.rescheduleAll(days, prefs, loc)
            }
        }
    }

    fun refreshPrayerTimes() {
        viewModelScope.launch {
            val currentLoc = container.preferencesRepository.getSelectedLocation()
            refreshPrayerTimesFor(currentLoc)
        }
    }

    private suspend fun refreshPrayerTimesFor(location: SelectedLocation) {
        syncState.update { it.copy(isRefreshing = true, message = null) }
        val result = container.prayerTimesRepository.refreshMonthlyPrayerTimes(location)
        syncState.update {
            SyncBannerState(
                isRefreshing = false,
                usedCacheFallback = result.usedCacheFallback,
                message = result.message
            )
        }
    }

    fun refreshPermissions() {
        permissionState.value = readPermissions()
    }

    private fun readPermissions(): PermissionStatusUi {
        return PermissionStatusUi(
            hasNotificationPermission = container.notificationHelper.hasNotificationPermission(),
            canScheduleExactAlarms = container.alarmScheduler.canScheduleExactAlarms()
        )
    }

    fun togglePrayerEnabled(type: PrayerType, enabled: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.setPrayerEnabled(type, enabled)
        }
    }

    fun selectSoundType(soundType: SoundType) {
        viewModelScope.launch {
            container.preferencesRepository.setSoundType(soundType)
        }
    }

    fun sendTestNotification() {
        viewModelScope.launch {
            val prefs = container.preferencesRepository.getNotificationPreferences()
            val title = appContext.getString(R.string.notif_test_title)
            val body = appContext.getString(R.string.notif_test_body)
            EzanPlaybackService.startOrFallback(
                context = appContext,
                notificationId = NotificationHelper.TEST_NOTIFICATION_ID,
                title = title,
                body = body,
                soundType = prefs.soundType,
                notificationHelper = container.notificationHelper,
                soundPlayerManager = container.soundPlayerManager
            )
        }
    }

    fun stopSound() {
        EzanPlaybackService.stopPlayback(appContext, container.soundPlayerManager)
    }

    // Location Picker Actions
    fun openLocationPicker() {
        pickerState.update {
            it.copy(
                step = LocationStep.COUNTRY,
                searchQuery = "",
                pendingCountry = null,
                pendingCity = null,
                isLoadingList = true
            )
        }
        viewModelScope.launch {
            container.prayerTimesRepository.refreshCountries()
            pickerState.update { it.copy(isLoadingList = false) }
        }
    }

    fun updateLocationSearchQuery(query: String) {
        pickerState.update { it.copy(searchQuery = query) }
    }

    fun selectCountry(country: CountryOption) {
        selectedCountryIdForPicker.value = country.id
        pickerState.update {
            it.copy(
                step = LocationStep.CITY,
                pendingCountry = country,
                pendingCity = null,
                searchQuery = "",
                isLoadingList = true
            )
        }
        viewModelScope.launch {
            container.prayerTimesRepository.refreshCities(country.id)
            pickerState.update { it.copy(isLoadingList = false) }
        }
    }

    fun selectCity(city: CityOption) {
        selectedCityIdForPicker.value = city.id
        pickerState.update {
            it.copy(
                step = LocationStep.DISTRICT,
                pendingCity = city,
                searchQuery = "",
                isLoadingList = true
            )
        }
        viewModelScope.launch {
            container.prayerTimesRepository.refreshDistricts(city.id)
            pickerState.update { it.copy(isLoadingList = false) }
        }
    }

    fun selectDistrict(district: DistrictOption, onComplete: () -> Unit) {
        val currentPicker = pickerState.value
        val country = currentPicker.pendingCountry ?: return
        val city = currentPicker.pendingCity ?: return
        val newLocation = SelectedLocation(
            countryId = country.id,
            countryName = country.name,
            cityId = city.id,
            cityName = city.name,
            districtId = district.id,
            districtName = district.name
        )
        viewModelScope.launch {
            container.preferencesRepository.saveSelectedLocation(newLocation)
            onComplete()
        }
    }

    fun navigateLocationStepBack(): Boolean {
        return when (pickerState.value.step) {
            LocationStep.DISTRICT -> {
                pickerState.update {
                    it.copy(step = LocationStep.CITY, pendingCity = null, searchQuery = "")
                }
                true
            }
            LocationStep.CITY -> {
                pickerState.update {
                    it.copy(step = LocationStep.COUNTRY, pendingCountry = null, searchQuery = "")
                }
                true
            }
            LocationStep.COUNTRY -> false
        }
    }

    private data class SyncBannerState(
        val isRefreshing: Boolean = false,
        val usedCacheFallback: Boolean = false,
        val message: String? = null
    )

    companion object {
        fun factory(appContext: Context, container: AppContainer): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PrayerViewModel(appContext.applicationContext, container) as T
                }
            }
        }
    }
}
