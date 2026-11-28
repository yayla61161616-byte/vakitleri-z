package com.example.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.data.repository.PrayerTimesRepository
import com.example.domain.SelectedLocation
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

data class DeviceCoordinates(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double,
    val accuracyMeters: Float,
    val areaLabel: String?
)

class DeviceLocationDetector(
    private val context: Context,
    private val repository: PrayerTimesRepository
) {

    fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasLocationPermission(): Boolean {
        val fine = hasFineLocationPermission()
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    suspend fun getCurrentCoordinates(): Result<DeviceCoordinates> = withContext(Dispatchers.IO) {
        runCatching {
            if (!hasLocationPermission()) {
                error("Kıble açısını hesaplamak için konum izni gerekli.")
            }
            val location = getCurrentOrLastLocation(preferHighAccuracy = hasFineLocationPermission())
                ?: error("Cihaz GPS konumu alınamadı. Lütfen konum servisini açıp tekrar deneyin.")

            val areaLabel = runCatching {
                val addr = reverseGeocode(location.latitude, location.longitude)
                listOfNotNull(addr?.subAdminArea, addr?.adminArea ?: addr?.locality)
                    .distinct()
                    .joinToString(", ")
                    .ifBlank { null }
            }.getOrNull()

            DeviceCoordinates(
                latitude = location.latitude,
                longitude = location.longitude,
                altitudeMeters = if (location.hasAltitude()) location.altitude else 0.0,
                accuracyMeters = location.accuracy,
                areaLabel = areaLabel
            )
        }
    }

    suspend fun detectAndResolveLocation(): Result<SelectedLocation> = withContext(Dispatchers.IO) {
        runCatching {
            if (!hasLocationPermission()) {
                error("Konum izni verilmedi.")
            }

            val location = getCurrentOrLastLocation(preferHighAccuracy = hasFineLocationPermission())
                ?: error("Konum alınamadı. Lütfen cihazınızın konum (GPS) servisinin açık olduğundan emin olun.")

            val address = reverseGeocode(location.latitude, location.longitude)
            if (address == null) {
                return@runCatching coordinateSelectedLocation(
                    lat = location.latitude,
                    lon = location.longitude,
                    countryName = "Konum",
                    cityName = "GPS Koordinatı",
                    districtName = String.format(Locale.US, "%.4f, %.4f", location.latitude, location.longitude)
                )
            }

            val isTurkey = address.countryCode.equals("TR", ignoreCase = true)
            if (isTurkey && address.subLocality.isNullOrBlank()) {
                runCatching { resolveToDiyanetLocation(address) }.getOrElse {
                    coordinateLocationFromAddress(location.latitude, location.longitude, address)
                }
            } else {
                // Outside Turkey (e.g. Ludwigshafen Oggersheim, Pfalz) or distinct village/suburb:
                // preserve exact GPS coordinates for village-level Diyanet Method 13 calculation.
                coordinateLocationFromAddress(location.latitude, location.longitude, address)
            }
        }
    }

    private fun coordinateLocationFromAddress(
        lat: Double,
        lon: Double,
        address: Address
    ): SelectedLocation {
        val villageOrDistrict = address.subLocality?.takeIf { it.isNotBlank() }
            ?: address.locality?.takeIf { it.isNotBlank() }
            ?: address.subAdminArea?.takeIf { it.isNotBlank() }
            ?: address.adminArea?.takeIf { it.isNotBlank() }
            ?: "GPS Konumu"
        val cityOrRegion = listOfNotNull(
            address.locality?.takeIf { it.isNotBlank() && !it.equals(villageOrDistrict, ignoreCase = true) },
            address.subAdminArea?.takeIf { it.isNotBlank() && !it.equals(villageOrDistrict, ignoreCase = true) },
            address.adminArea?.takeIf { it.isNotBlank() && !it.equals(villageOrDistrict, ignoreCase = true) }
        ).distinct().take(2).joinToString(", ")

        return coordinateSelectedLocation(
            lat = lat,
            lon = lon,
            countryName = address.countryName ?: "Konum",
            cityName = cityOrRegion.ifBlank { address.adminArea ?: "" },
            districtName = villageOrDistrict
        )
    }

    private fun coordinateSelectedLocation(
        lat: Double,
        lon: Double,
        countryName: String,
        cityName: String,
        districtName: String
    ): SelectedLocation {
        return SelectedLocation(
            countryId = "geo",
            countryName = countryName,
            cityId = "geo",
            cityName = cityName,
            districtId = SelectedLocation.coordinateDistrictId(lat, lon),
            districtName = districtName,
            latitude = lat,
            longitude = lon
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun getCurrentOrLastLocation(preferHighAccuracy: Boolean): Location? {
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        val priority = if (preferHighAccuracy) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val currentFused: Location? = withTimeoutOrNull(8_000L) {
            suspendCancellableCoroutine { cont ->
                val cts = CancellationTokenSource()
                cont.invokeOnCancellation { cts.cancel() }
                fusedClient.getCurrentLocation(priority, cts.token)
                    .addOnSuccessListener { loc ->
                        if (cont.isActive) cont.resume(loc)
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume(null)
                    }
            }
        }
        if (currentFused != null) return currentFused

        val lastFused: Location? = suspendCancellableCoroutine { cont ->
            fusedClient.lastLocation
                .addOnSuccessListener { loc ->
                    if (cont.isActive) cont.resume(loc)
                }
                .addOnFailureListener {
                    if (cont.isActive) cont.resume(null)
                }
        }
        if (lastFused != null) return lastFused

        // Fallback to Android LocationManager
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = lm.getProviders(true)
        var best: Location? = null
        for (provider in providers) {
            val l = runCatching { lm.getLastKnownLocation(provider) }.getOrNull() ?: continue
            if (best == null || l.accuracy < best.accuracy) {
                best = l
            }
        }
        return best
    }

    private suspend fun reverseGeocode(lat: Double, lon: Double): Address? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.forLanguageTag("tr-TR"))

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(lat, lon, 1) { addresses ->
                    if (cont.isActive) cont.resume(addresses.firstOrNull())
                }
            }
        } else {
            @Suppress("DEPRECATION")
            runCatching { geocoder.getFromLocation(lat, lon, 1)?.firstOrNull() }.getOrNull()
        }
    }

    private suspend fun resolveToDiyanetLocation(address: Address): SelectedLocation {
        repository.refreshCountries()
        val countries = repository.observeCountries().first()

        val countryRaw = address.countryName.orEmpty()
        val countryCode = address.countryCode.orEmpty()
        val matchedCountry = when {
            countryCode.equals("TR", ignoreCase = true) ||
                normalizeKey(countryRaw).contains("turk") ->
                countries.firstOrNull { it.id == "2" } ?: countries.first()
            else -> countries.firstOrNull {
                normalizeKey(it.name) == normalizeKey(countryRaw) ||
                    normalizeKey(it.nameEn) == normalizeKey(countryRaw) ||
                    normalizeKey(it.name).contains(normalizeKey(countryRaw)) ||
                    normalizeKey(it.nameEn).contains(normalizeKey(countryRaw))
            } ?: countries.firstOrNull { it.id == "2" } ?: countries.first()
        }

        repository.refreshCities(matchedCountry.id)
        val cities = repository.observeCities(matchedCountry.id).first()

        val candidateCityNames = listOfNotNull(
            address.adminArea,
            address.locality,
            address.subAdminArea
        ).map { normalizeKey(it) }.filter { it.isNotBlank() }

        val matchedCity = candidateCityNames.firstNotNullOfOrNull { query ->
            cities.firstOrNull { normalizeKey(it.name) == query || normalizeKey(it.nameEn) == query }
                ?: cities.firstOrNull {
                    normalizeKey(it.name).contains(query) || query.contains(normalizeKey(it.name))
                }
        } ?: cities.firstOrNull()
            ?: error("${matchedCountry.name} için şehir listesi alınamadı.")

        repository.refreshDistricts(matchedCity.id)
        val districts = repository.observeDistricts(matchedCity.id).first()

        val candidateDistrictNames = listOfNotNull(
            address.subAdminArea,
            address.locality,
            address.subLocality
        ).map { normalizeKey(it) }.filter { it.isNotBlank() }

        val matchedDistrict = candidateDistrictNames.firstNotNullOfOrNull { query ->
            districts.firstOrNull { normalizeKey(it.name) == query || normalizeKey(it.nameEn) == query }
                ?: districts.firstOrNull {
                    normalizeKey(it.name).contains(query) || query.contains(normalizeKey(it.name))
                }
        } ?: districts.firstOrNull {
            normalizeKey(it.name) == normalizeKey(matchedCity.name) ||
                normalizeKey(it.name).contains("merkez")
        } ?: districts.firstOrNull()
            ?: error("${matchedCity.name} için ilçe bulunamadı.")

        return SelectedLocation(
            countryId = matchedCountry.id,
            countryName = matchedCountry.name,
            cityId = matchedCity.id,
            cityName = matchedCity.name,
            districtId = matchedDistrict.id,
            districtName = matchedDistrict.name
        )
    }

    private fun normalizeKey(text: String): String {
        val tr = Locale.forLanguageTag("tr-TR")
        return text.trim()
            .lowercase(tr)
            .replace("province", "")
            .replace("ili", "")
            .replace("ilçesi", "")
            .replace('ı', 'i')
            .replace('ğ', 'g')
            .replace('ü', 'u')
            .replace('ş', 's')
            .replace('ö', 'o')
            .replace('ç', 'c')
            .replace(Regex("[^a-z0-9]"), "")
    }
}
