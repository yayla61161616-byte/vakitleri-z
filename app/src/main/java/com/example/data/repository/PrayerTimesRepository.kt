package com.example.data.repository

import com.example.data.local.CityEntity
import com.example.data.local.CountryEntity
import com.example.data.local.DistrictEntity
import com.example.data.local.PrayerDao
import com.example.data.local.PrayerDayEntity
import com.example.data.local.SeedLocations
import com.example.data.remote.AladhanApiService
import com.example.data.remote.AladhanDayDto
import com.example.data.remote.DiyanetApiService
import com.example.domain.CityOption
import com.example.domain.CountryOption
import com.example.domain.DistrictOption
import com.example.domain.PrayerDay
import com.example.domain.PrayerTimeCalculator
import com.example.domain.SelectedLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.Locale

data class RefreshResult(
    val usedCacheFallback: Boolean,
    val sourceLabel: String,
    val message: String? = null
)

class PrayerTimesRepository(
    private val prayerDao: PrayerDao,
    private val diyanetApi: DiyanetApiService,
    private val aladhanApi: AladhanApiService
) {

    fun observeUpcomingPrayerDays(districtId: String): Flow<List<PrayerDay>> {
        val todayIso = PrayerTimeCalculator.todayIsoDate()
        return prayerDao.observeUpcomingPrayerDays(districtId, todayIso)
            .map { entities -> entities.map { it.toDomain() } }
    }

    suspend fun getUpcomingPrayerDays(districtId: String, limit: Int = 7): List<PrayerDay> {
        val todayIso = PrayerTimeCalculator.todayIsoDate()
        return prayerDao.getUpcomingPrayerDays(districtId, todayIso, limit).map { it.toDomain() }
    }

    suspend fun refreshMonthlyPrayerTimes(location: SelectedLocation): RefreshResult {
        val now = System.currentTimeMillis()
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
        now: Long
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
            sourceLabel = "Diyanet Metodu (Yedek Sunucu)",
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
