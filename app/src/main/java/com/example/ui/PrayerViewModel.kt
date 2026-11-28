package com.example.ui

import android.content.Context
import android.hardware.GeomagneticField
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.R
import com.example.alarm.EzanPlaybackService
import com.example.alarm.NotificationHelper
import com.example.data.location.DeviceCoordinates
import com.example.domain.CityOption
import com.example.domain.CountryOption
import com.example.domain.DistrictOption
import com.example.domain.NotificationPreferences
import com.example.domain.PlaceSearchResult
import com.example.domain.PrayerCountdownStatus
import com.example.domain.PrayerDay
import com.example.domain.PrayerTimeCalculator
import com.example.domain.PrayerType
import com.example.domain.QiblaCalculator
import com.example.domain.SelectedLocation
import com.example.domain.SoundType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
    val canScheduleExactAlarms: Boolean = true,
    val hasLocationPermission: Boolean = false
)

enum class LocationStep { COUNTRY, CITY, DISTRICT }

enum class LocationPickerMode { DIYANET_LIST, VILLAGE_SEARCH }

data class LocationPickerUiState(
    val mode: LocationPickerMode = LocationPickerMode.DIYANET_LIST,
    val step: LocationStep = LocationStep.COUNTRY,
    val searchQuery: String = "",
    val countries: List<CountryOption> = emptyList(),
    val cities: List<CityOption> = emptyList(),
    val districts: List<DistrictOption> = emptyList(),
    val pendingCountry: CountryOption? = null,
    val pendingCity: CityOption? = null,
    val isLoadingList: Boolean = false,
    val villageResults: List<PlaceSearchResult> = emptyList(),
    val isSearchingVillages: Boolean = false,
    val villageSearchError: String? = null
)

data class QiblaUiState(
    val coordinates: DeviceCoordinates? = null,
    val qiblaBearingDegrees: Double? = null,
    val distanceKm: Double? = null,
    val magneticDeclinationDegrees: Float = 0f,
    val isFetchingGps: Boolean = false,
    val errorMessage: String? = null
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
    val hasBundledCustomEzanFile: Boolean = container.soundPlayerManager.hasBundledCustomEzanFile()

    private val _customAudioStatus = MutableStateFlow<String?>(null)
    val customAudioStatus: StateFlow<String?> = _customAudioStatus.asStateFlow()

    private val _isDetectingLocation = MutableStateFlow(false)
    val isDetectingLocation: StateFlow<Boolean> = _isDetectingLocation.asStateFlow()

    private val _locationDetectMessage = MutableStateFlow<String?>(null)
    val locationDetectMessage: StateFlow<String?> = _locationDetectMessage.asStateFlow()

    val monthlyPrayerDaysState: StateFlow<List<PrayerDay>> = selectedLocationFlow
        .flatMapLatest { loc ->
            container.prayerTimesRepository.observeMonthlyPrayerDays(loc.districtId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _qiblaUiState = MutableStateFlow(QiblaUiState())
    val qiblaUiState: StateFlow<QiblaUiState> = _qiblaUiState.asStateFlow()

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

        // Reschedule AlarmManager alarms & update persistent countdown notification bar
        viewModelScope.launch {
            combine(
                upcomingDaysFlow,
                preferencesFlow,
                selectedLocationFlow,
                permissionState
            ) { days, prefs, loc, _ ->
                Triple(days, prefs, loc)
            }.collect { (days, prefs, loc) ->
                container.alarmScheduler.rescheduleAll(days, prefs, loc)
                container.notificationHelper.updateCountdownNotification(
                    days = days,
                    location = loc,
                    enabled = prefs.persistentCountdownEnabled
                )
                if (prefs.persistentCountdownEnabled && days.isNotEmpty()) {
                    container.alarmScheduler.scheduleNextCountdownRefresh()
                } else {
                    container.alarmScheduler.cancelCountdownRefresh()
                }
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
            canScheduleExactAlarms = container.alarmScheduler.canScheduleExactAlarms(),
            hasLocationPermission = container.locationDetector.hasLocationPermission()
        )
    }

    fun togglePrayerEnabled(type: PrayerType, enabled: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.setPrayerEnabled(type, enabled)
        }
    }

    fun togglePersistentCountdown(enabled: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.setPersistentCountdownEnabled(enabled)
        }
    }

    fun detectCurrentLocation(onSuccess: () -> Unit = {}) {
        if (_isDetectingLocation.value) return
        viewModelScope.launch {
            _isDetectingLocation.value = true
            _locationDetectMessage.value = null
            val result = container.locationDetector.detectAndResolveLocation()
            result.onSuccess { resolved ->
                container.preferencesRepository.saveSelectedLocation(resolved)
                _locationDetectMessage.value = "Konum algılandı: ${resolved.formattedDisplay()}"
                _isDetectingLocation.value = false
                onSuccess()
            }.onFailure { err ->
                _locationDetectMessage.value = err.message ?: "Konum alınamadı."
                _isDetectingLocation.value = false
            }
        }
    }

    fun clearLocationDetectMessage() {
        _locationDetectMessage.value = null
    }

    fun refreshQiblaGps() {
        if (_qiblaUiState.value.isFetchingGps) return
        viewModelScope.launch {
            _qiblaUiState.update { it.copy(isFetchingGps = true, errorMessage = null) }
            val result = container.locationDetector.getCurrentCoordinates()
            result.onSuccess { coords ->
                val bearing = QiblaCalculator.calculateBearingDegrees(coords.latitude, coords.longitude)
                val distance = QiblaCalculator.calculateDistanceKm(coords.latitude, coords.longitude)
                val declination = GeomagneticField(
                    coords.latitude.toFloat(),
                    coords.longitude.toFloat(),
                    coords.altitudeMeters.toFloat(),
                    System.currentTimeMillis()
                ).declination
                _qiblaUiState.value = QiblaUiState(
                    coordinates = coords,
                    qiblaBearingDegrees = bearing,
                    distanceKm = distance,
                    magneticDeclinationDegrees = declination,
                    isFetchingGps = false,
                    errorMessage = null
                )
            }.onFailure { err ->
                _qiblaUiState.update {
                    it.copy(
                        isFetchingGps = false,
                        errorMessage = err.message ?: "GPS konumu alınamadı."
                    )
                }
            }
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

    fun importCustomAudio(uri: Uri) {
        viewModelScope.launch {
            _customAudioStatus.value = "Ses dosyası yükleniyor…"
            val result = container.soundPlayerManager.importCustomAudio(uri)
            result.onSuccess { displayName ->
                container.preferencesRepository.setCustomSoundName(displayName)
                container.preferencesRepository.setSoundType(SoundType.EZAN)
                _customAudioStatus.value = "Özel ses eklendi: $displayName"
            }.onFailure { error ->
                _customAudioStatus.value = error.message ?: "Ses dosyası eklenemedi."
            }
        }
    }

    fun clearCustomAudio() {
        viewModelScope.launch {
            container.soundPlayerManager.clearCustomAudio()
            container.preferencesRepository.setCustomSoundName(null)
            _customAudioStatus.value = "Özel ses kaldırıldı. Yerleşik sese dönüldü."
        }
    }

    private var villageSearchJob: Job? = null

    // Location Picker Actions
    fun openLocationPicker() {
        villageSearchJob?.cancel()
        pickerState.update {
            it.copy(
                mode = LocationPickerMode.DIYANET_LIST,
                step = LocationStep.COUNTRY,
                searchQuery = "",
                pendingCountry = null,
                pendingCity = null,
                isLoadingList = true,
                villageResults = emptyList(),
                isSearchingVillages = false,
                villageSearchError = null
            )
        }
        viewModelScope.launch {
            container.prayerTimesRepository.refreshCountries()
            pickerState.update { it.copy(isLoadingList = false) }
        }
    }

    fun setLocationPickerMode(mode: LocationPickerMode) {
        val currentQuery = pickerState.value.searchQuery
        pickerState.update {
            it.copy(
                mode = mode,
                villageSearchError = null
            )
        }
        if (mode == LocationPickerMode.VILLAGE_SEARCH && currentQuery.trim().length >= 3) {
            triggerVillageSearch(currentQuery, immediate = true)
        }
    }

    fun switchToVillageSearchWithQuery(query: String) {
        pickerState.update {
            it.copy(
                mode = LocationPickerMode.VILLAGE_SEARCH,
                searchQuery = query,
                villageSearchError = null
            )
        }
        triggerVillageSearch(query, immediate = true)
    }

    fun updateLocationSearchQuery(query: String) {
        pickerState.update { it.copy(searchQuery = query, villageSearchError = null) }
        if (pickerState.value.mode == LocationPickerMode.VILLAGE_SEARCH) {
            triggerVillageSearch(query, immediate = false)
        }
    }

    fun searchVillagesNow() {
        triggerVillageSearch(pickerState.value.searchQuery, immediate = true)
    }

    private fun triggerVillageSearch(query: String, immediate: Boolean) {
        villageSearchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            pickerState.update {
                it.copy(villageResults = emptyList(), isSearchingVillages = false, villageSearchError = null)
            }
            return
        }
        villageSearchJob = viewModelScope.launch {
            if (!immediate) {
                delay(650L)
            }
            pickerState.update { it.copy(isSearchingVillages = true, villageSearchError = null) }
            val results = runCatching {
                container.prayerTimesRepository.searchVillagesAndPlaces(trimmed, appContext)
            }
            results.onSuccess { list ->
                pickerState.update {
                    it.copy(
                        villageResults = list,
                        isSearchingVillages = false,
                        villageSearchError = if (list.isEmpty()) {
                            "Sonuç bulunamadı. Örnek: \"Ludwigshafen Oggersheim\" veya \"Oggersheim Pfalz\" yazmayı deneyin."
                        } else {
                            null
                        }
                    )
                }
            }.onFailure {
                pickerState.update {
                    it.copy(
                        isSearchingVillages = false,
                        villageSearchError = "Konum araması yapılamadı. İnternet bağlantınızı kontrol edin."
                    )
                }
            }
        }
    }

    fun selectPlaceSearchResult(place: PlaceSearchResult, onComplete: () -> Unit) {
        val newLocation = SelectedLocation(
            countryId = "geo",
            countryName = place.countryName,
            cityId = "geo",
            cityName = place.cityOrRegion,
            districtId = SelectedLocation.coordinateDistrictId(place.latitude, place.longitude),
            districtName = place.primaryName,
            latitude = place.latitude,
            longitude = place.longitude
        )
        viewModelScope.launch {
            container.preferencesRepository.saveSelectedLocation(newLocation)
            onComplete()
        }
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
        if (pickerState.value.mode == LocationPickerMode.VILLAGE_SEARCH) {
            return false
        }
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
