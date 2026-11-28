package com.example.data.repository

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.example.data.local.CityEntity
import com.example.data.local.CountryEntity
import com.example.data.local.DistrictEntity
import com.example.data.local.PrayerDao
import com.example.data.local.PrayerDayEntity
import com.example.data.local.SeedLocations
import com.example.data.remote.AladhanApiService
import com.example.data.remote.AladhanDayDto
import com.example.data.remote.DiyanetApiService
import com.example.data.remote.NominatimApiService
import com.example.data.remote.NominatimPlaceDto
import com.example.domain.CityOption
import com.example.domain.CountryOption
import com.example.domain.DistrictOption
import com.example.domain.PlaceSearchResult
import com.example.domain.PrayerDay
import com.example.domain.PrayerTimeCalculator
import com.example.domain.SelectedLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import kotlin.coroutines.resume

data class RefreshResult(
    val usedCacheFallback: Boolean,
    val sourceLabel: String,
    val message: String? = null
)

class PrayerTimesRepository(
    private val prayerDao: PrayerDao,
    private val diyanetApi: DiyanetApiService,
    private val aladhanApi: AladhanApiService,
    private val nominatimApi: NominatimApiService
) {

    fun observeUpcomingPrayerDays(districtId: String): Flow<List<PrayerDay>> {
        val todayIso = PrayerTimeCalculator.todayIsoDate()
        return prayerDao.observeUpcomingPrayerDays(districtId, todayIso)
            .map { entities -> entities.map { it.toDomain() } }
    }

    fun observeMonthlyPrayerDays(districtId: String): Flow<List<PrayerDay>> {
        return prayerDao.observeAllPrayerDays(districtId)
            .map { entities -> entities.map { it.toDomain() } }
    }

    suspend fun getUpcomingPrayerDays(districtId: String, limit: Int = 7): List<PrayerDay> {
        val todayIso = PrayerTimeCalculator.todayIsoDate()
        return prayerDao.getUpcomingPrayerDays(districtId, todayIso, limit).map { it.toDomain() }
    }

    suspend fun refreshMonthlyPrayerTimes(location: SelectedLocation): RefreshResult {
        val now = System.currentTimeMillis()

        // 0. Exact Coordinate Mode (villages, neighborhoods, Ortsteil/Stadtteil worldwide)
        val lat = location.latitude
        val lon = location.longitude
        if (lat != null && lon != null) {
            val coordResult = runCatching {
                val cal = Calendar.getInstance()
                val currentYear = cal.get(Calendar.YEAR)
                val currentMonth = cal.get(Calendar.MONTH) + 1

                val thisMonthResp = aladhanApi.getMonthlyByCoordinates(
                    year = currentYear,
                    month = currentMonth,
                    latitude = lat,
                    longitude = lon
                )

                val nextCal = (cal.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
                val nextMonthDays = if (cal.get(Calendar.DAY_OF_MONTH) >= 20) {
                    runCatching {
                        aladhanApi.getMonthlyByCoordinates(
                            year = nextCal.get(Calendar.YEAR),
                            month = nextCal.get(Calendar.MONTH) + 1,
                            latitude = lat,
                            longitude = lon
                        ).data
                    }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }

                val entities = (thisMonthResp.data + nextMonthDays).mapNotNull { dto ->
                    mapAladhanDayToEntity(
                        districtId = location.districtId,
                        dto = dto,
                        now = now,
                        sourceLabel = "Diyanet Metodu • Koordinat Hassasiyeti"
                    )
                }
                if (entities.isEmpty()) error("Empty coordinate response")
                prayerDao.upsertPrayerDays(entities)
                RefreshResult(
                    usedCacheFallback = false,
                    sourceLabel = "Diyanet Metodu • Koordinat Hassasiyeti"
                )
            }
            if (coordResult.isSuccess) {
                return coordResult.getOrThrow()
            }
        } else {
            // 1. Primary: Official Diyanet-aligned EzanVakti endpoint
            val diyanetResult = runCatching {
                val remoteDays = diyanetApi.getPrayerTimes(location.districtId)
                val entities = remoteDays.mapNotNull { dto ->
                    val iso = PrayerTimeCalculator.convertShortMiladiToIso(dto.miladiTarihKisa)
                        ?: return@mapNotNull null
                    PrayerDayEntity(
                        districtId = location.districtId,
                        isoDate = iso,
                        miladiShort = dto.miladiTarihKisa,
                        miladiLong = dto.miladiTarihUzun?.takeIf { it.isNotBlank() }
                            ?: dto.miladiTarihKisa,
                        hicriLong = dto.hicriTarihUzun?.takeIf { it.isNotBlank() } ?: "",
                        imsak = dto.imsak.take(5),
                        gunes = dto.gunes.take(5),
                        ogle = dto.ogle.take(5),
                        ikindi = dto.ikindi.take(5),
                        aksam = dto.aksam.take(5),
                        yatsi = dto.yatsi.take(5),
                        sourceLabel = "Diyanet İşleri Başkanlığı",
                        updatedAtMillis = now
                    )
                }
                if (entities.isEmpty()) error("Empty Diyanet response")
                prayerDao.upsertPrayerDays(entities)
                RefreshResult(
                    usedCacheFallback = false,
                    sourceLabel = "Diyanet İşleri Başkanlığı"
                )
            }

            if (diyanetResult.isSuccess) {
                return diyanetResult.getOrThrow()
            }

            // 2. Fallback API: Aladhan Method 13 (Diyanet İşleri Başkanlığı, Turkey)
            val aladhanResult = runCatching {
                val cal = Calendar.getInstance()
                val currentYear = cal.get(Calendar.YEAR)
                val currentMonth = cal.get(Calendar.MONTH) + 1

                val cityQuery = normalizeCityForAladhan(location.cityName)
                val countryQuery = normalizeCountryForAladhan(location.countryName)

                val thisMonthResp = aladhanApi.getMonthlyByCity(
                    year = currentYear,
                    month = currentMonth,
                    city = cityQuery,
                    country = countryQuery
                )

                // Also fetch next month if near month-end so tomorrow countdown always has data
                val nextCal = (cal.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
                val nextMonthDays = if (cal.get(Calendar.DAY_OF_MONTH) >= 25) {
                    runCatching {
                        aladhanApi.getMonthlyByCity(
                            year = nextCal.get(Calendar.YEAR),
                            month = nextCal.get(Calendar.MONTH) + 1,
                            city = cityQuery,
                            country = countryQuery
                        ).data
                    }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }

                val allDays = thisMonthResp.data + nextMonthDays
                val entities = allDays.mapNotNull { dto ->
                    mapAladhanDayToEntity(location.districtId, dto, now)
                }
                if (entities.isEmpty()) error("Empty Aladhan Method 13 response")
                prayerDao.upsertPrayerDays(entities)
                RefreshResult(
                    usedCacheFallback = false,
                    sourceLabel = "Diyanet Metodu (Yedek Sunucu)"
                )
            }

            if (aladhanResult.isSuccess) {
                return aladhanResult.getOrThrow()
            }
        }

        // 3. Offline Room Cache Fallback
        val cached = getUpcomingPrayerDays(location.districtId, limit = 1)
        return if (cached.isNotEmpty()) {
            RefreshResult(
                usedCacheFallback = true,
                sourceLabel = cached.first().sourceLabel,
                message = "İnternet bağlantısı yok. Kayıtlı son vakitler gösteriliyor."
            )
        } else {
            RefreshResult(
                usedCacheFallback = true,
                sourceLabel = "Çevrimdışı",
                message = "Vakitler sunucudan alınamadı. Lütfen internet bağlantınızı kontrol edip tekrar deneyin."
            )
        }
    }

    suspend fun searchVillagesAndPlaces(query: String, context: Context): List<PlaceSearchResult> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.length < 2) return@withContext emptyList()

            val nominatimResults = runCatching {
                nominatimApi.searchPlaces(query = trimmed).mapNotNull { it.toPlaceSearchResult() }
            }.getOrDefault(emptyList())

            if (nominatimResults.isNotEmpty()) {
                return@withContext nominatimResults
            }

            // Fallback to Android Geocoder if available
            if (!Geocoder.isPresent()) return@withContext emptyList()
            val geocoder = Geocoder(context, Locale.forLanguageTag("tr-TR"))
            val addresses: List<Address> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocationName(trimmed, 8) { list ->
                        if (cont.isActive) cont.resume(list)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocationName(trimmed, 8).orEmpty() }.getOrDefault(emptyList())
            }

            addresses.mapIndexedNotNull { idx, addr ->
                if (!addr.hasLatitude() || !addr.hasLongitude()) return@mapIndexedNotNull null
                val primary = addr.subLocality
                    ?: addr.locality
                    ?: addr.subAdminArea
                    ?: addr.featureName
                    ?: return@mapIndexedNotNull null
                val cityRegion = listOfNotNull(
                    addr.locality?.takeIf { !it.equals(primary, ignoreCase = true) },
                    addr.subAdminArea?.takeIf { !it.equals(primary, ignoreCase = true) },
                    addr.adminArea
                ).distinct().joinToString(", ")
                val country = addr.countryName ?: ""
                val subtitle = listOf(cityRegion, country).filter { it.isNotBlank() }.joinToString(" • ")
                PlaceSearchResult(
                    id = "gc_${idx}_${addr.latitude}_${addr.longitude}",
                    primaryName = primary,
                    cityOrRegion = cityRegion.ifBlank { primary },
                    countryName = country.ifBlank { "Konum" },
                    subtitle = subtitle,
                    latitude = addr.latitude,
                    longitude = addr.longitude
                )
            }
        }

    private fun NominatimPlaceDto.toPlaceSearchResult(): PlaceSearchResult? {
        val latVal = lat.toDoubleOrNull() ?: return null
        val lonVal = lon.toDoubleOrNull() ?: return null
        val addr = address
        val primary = addr?.village
            ?: addr?.hamlet
            ?: addr?.suburb
            ?: addr?.quarter
            ?: addr?.neighbourhood
            ?: addr?.cityDistrict
            ?: addr?.town
            ?: addr?.city
            ?: name?.takeIf { it.isNotBlank() }
            ?: displayName.substringBefore(",").trim()

        val cityPart = listOfNotNull(
            addr?.town?.takeIf { !it.equals(primary, ignoreCase = true) },
            addr?.city?.takeIf { !it.equals(primary, ignoreCase = true) },
            addr?.municipality?.takeIf { !it.equals(primary, ignoreCase = true) },
            addr?.county?.takeIf { !it.equals(primary, ignoreCase = true) },
            addr?.state?.takeIf { !it.equals(primary, ignoreCase = true) }
        ).distinct().take(2).joinToString(", ")

        val country = addr?.country?.takeIf { it.isNotBlank() } ?: "Konum"
        val subtitle = listOf(cityPart, country)
            .filter { it.isNotBlank() }
            .joinToString(" • ")
            .ifBlank { displayName }

        return PlaceSearchResult(
            id = "nom_${placeId ?: "${latVal}_${lonVal}"}",
            primaryName = primary,
            cityOrRegion = cityPart.ifBlank { addr?.state ?: primary },
            countryName = country,
            subtitle = subtitle,
            latitude = latVal,
            longitude = lonVal
        )
    }

    suspend fun ensureLocationSeedsLoaded() {
        if (prayerDao.countCountries() == 0) {
            prayerDao.upsertCountries(SeedLocations.countries)
        }
        if (prayerDao.countCities("2") == 0) {
            prayerDao.upsertCities(SeedLocations.turkeyCities)
        }
        if (prayerDao.countDistricts("539") == 0) {
            prayerDao.upsertDistricts(SeedLocations.popularDistricts)
        }
    }

    fun observeCountries(): Flow<List<CountryOption>> =
        prayerDao.observeCountries().map { list -> list.map { it.toDomain() } }

    suspend fun refreshCountries() {
        ensureLocationSeedsLoaded()
        runCatching {
            val remote = diyanetApi.getCountries()
            if (remote.isNotEmpty()) {
                prayerDao.upsertCountries(
                    remote.map {
                        CountryEntity(
                            id = it.ulkeId,
                            name = it.ulkeAdi,
                            nameEn = it.ulkeAdiEn ?: it.ulkeAdi
                        )
                    }
                )
            }
        }
    }

    fun observeCities(countryId: String): Flow<List<CityOption>> =
        prayerDao.observeCities(countryId).map { list -> list.map { it.toDomain() } }

    suspend fun refreshCities(countryId: String) {
        ensureLocationSeedsLoaded()
        runCatching {
            val remote = diyanetApi.getCities(countryId)
            if (remote.isNotEmpty()) {
                prayerDao.upsertCities(
                    remote.map {
                        CityEntity(
                            id = it.sehirId,
                            countryId = countryId,
                            name = it.sehirAdi,
                            nameEn = it.sehirAdiEn ?: it.sehirAdi
                        )
                    }
                )
            }
        }
    }

    fun observeDistricts(cityId: String): Flow<List<DistrictOption>> =
        prayerDao.observeDistricts(cityId).map { list -> list.map { it.toDomain() } }

    suspend fun refreshDistricts(cityId: String) {
        ensureLocationSeedsLoaded()
        runCatching {
            val remote = diyanetApi.getDistricts(cityId)
            if (remote.isNotEmpty()) {
                prayerDao.upsertDistricts(
                    remote.map {
                        DistrictEntity(
                            id = it.ilceId,
                            cityId = cityId,
                            name = it.ilceAdi,
                            nameEn = it.ilceAdiEn ?: it.ilceAdi
                        )
                    }
                )
            }
        }
    }

    private fun mapAladhanDayToEntity(
        districtId: String,
        dto: AladhanDayDto,
        now: Long,
        sourceLabel: String = "Diyanet Metodu (Yedek Sunucu)"
    ): PrayerDayEntity? {
        val parts = dto.date.gregorian.date.split("-") // DD-MM-YYYY
        if (parts.size != 3) return null
        val isoDate = "${parts[2]}-${parts[1]}-${parts[0]}"
        val miladiShort = "${parts[0]}.${parts[1]}.${parts[2]}"
        val turkishMonth = gregorianMonthTr(dto.date.gregorian.month.number)
        val turkishWeekday = weekdayTr(dto.date.gregorian.weekday.en)
        val miladiLong = "${dto.date.gregorian.day.toIntOrNull() ?: dto.date.gregorian.day} $turkishMonth ${dto.date.gregorian.year} $turkishWeekday"
        val hijriMonthTr = hijriMonthTr(dto.date.hijri.month.number, dto.date.hijri.month.en)
        val hicriLong = "${dto.date.hijri.day.toIntOrNull() ?: dto.date.hijri.day} $hijriMonthTr ${dto.date.hijri.year}"

        return PrayerDayEntity(
            districtId = districtId,
            isoDate = isoDate,
            miladiShort = miladiShort,
            miladiLong = miladiLong,
            hicriLong = hicriLong,
            imsak = dto.timings.fajr.cleanClock(),
            gunes = dto.timings.sunrise.cleanClock(),
            ogle = dto.timings.dhuhr.cleanClock(),
            ikindi = dto.timings.asr.cleanClock(),
            aksam = dto.timings.maghrib.cleanClock(),
            yatsi = dto.timings.isha.cleanClock(),
            sourceLabel = sourceLabel,
            updatedAtMillis = now
        )
    }

    private fun String.cleanClock(): String = substringBefore(" ").trim().take(5)

    private fun normalizeCityForAladhan(cityName: String): String {
        val tr = Locale.forLanguageTag("tr-TR")
        return cityName.lowercase(tr)
            .replace('ı', 'i')
            .replace('İ', 'I')
            .replace('ğ', 'g')
            .replace('ü', 'u')
            .replace('ş', 's')
            .replace('ö', 'o')
            .replace('ç', 'c')
            .replaceFirstChar { it.uppercase(Locale.US) }
    }

    private fun normalizeCountryForAladhan(countryName: String): String {
        return when {
            countryName.contains("TÜRK", ignoreCase = true) -> "Turkey"
            countryName.contains("ALMANYA", ignoreCase = true) -> "Germany"
            countryName.contains("HOLLANDA", ignoreCase = true) -> "Netherlands"
            countryName.contains("FRANSA", ignoreCase = true) -> "France"
            countryName.contains("İNGİLTERE", ignoreCase = true) -> "United Kingdom"
            countryName.contains("ABD", ignoreCase = true) -> "United States"
            else -> normalizeCityForAladhan(countryName)
        }
    }

    private fun gregorianMonthTr(month: Int): String = when (month) {
        1 -> "Ocak"
        2 -> "Şubat"
        3 -> "Mart"
        4 -> "Nisan"
        5 -> "Mayıs"
        6 -> "Haziran"
        7 -> "Temmuz"
        8 -> "Ağustos"
        9 -> "Eylül"
        10 -> "Ekim"
        11 -> "Kasım"
        12 -> "Aralık"
        else -> ""
    }

    private fun weekdayTr(en: String): String = when (en.lowercase(Locale.US)) {
        "monday" -> "Pazartesi"
        "tuesday" -> "Salı"
        "wednesday" -> "Çarşamba"
        "thursday" -> "Perşembe"
        "friday" -> "Cuma"
        "saturday" -> "Cumartesi"
        "sunday" -> "Pazar"
        else -> en
    }

    private fun hijriMonthTr(number: Int, fallback: String): String = when (number) {
        1 -> "Muharrem"
        2 -> "Safer"
        3 -> "Rebiülevvel"
        4 -> "Rebiülahir"
        5 -> "Cemaziyelevvel"
        6 -> "Cemaziyelahir"
        7 -> "Recep"
        8 -> "Şaban"
        9 -> "Ramazan"
        10 -> "Şevval"
        11 -> "Zilkade"
        12 -> "Zilhicce"
        else -> fallback
    }
}
