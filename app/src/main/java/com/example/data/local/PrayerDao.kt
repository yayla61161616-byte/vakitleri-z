package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PrayerDao {

    @Query("SELECT * FROM prayer_days WHERE districtId = :districtId AND isoDate >= :fromIsoDate ORDER BY isoDate ASC")
    fun observeUpcomingPrayerDays(districtId: String, fromIsoDate: String): Flow<List<PrayerDayEntity>>

    @Query("SELECT * FROM prayer_days WHERE districtId = :districtId AND isoDate >= :fromIsoDate ORDER BY isoDate ASC LIMIT :limit")
    suspend fun getUpcomingPrayerDays(districtId: String, fromIsoDate: String, limit: Int): List<PrayerDayEntity>

    @Query("SELECT * FROM prayer_days WHERE districtId = :districtId AND isoDate = :isoDate LIMIT 1")
    suspend fun getPrayerDay(districtId: String, isoDate: String): PrayerDayEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPrayerDays(days: List<PrayerDayEntity>)

    @Query("SELECT * FROM countries ORDER BY CASE WHEN id = '2' THEN 0 ELSE 1 END, name ASC")
    fun observeCountries(): Flow<List<CountryEntity>>

    @Query("SELECT COUNT(*) FROM countries")
    suspend fun countCountries(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCountries(countries: List<CountryEntity>)

    @Query("SELECT * FROM cities WHERE countryId = :countryId ORDER BY name ASC")
    fun observeCities(countryId: String): Flow<List<CityEntity>>

    @Query("SELECT COUNT(*) FROM cities WHERE countryId = :countryId")
    suspend fun countCities(countryId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCities(cities: List<CityEntity>)

    @Query("SELECT * FROM districts WHERE cityId = :cityId ORDER BY name ASC")
    fun observeDistricts(cityId: String): Flow<List<DistrictEntity>>

    @Query("SELECT COUNT(*) FROM districts WHERE cityId = :cityId")
    suspend fun countDistricts(cityId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDistricts(districts: List<DistrictEntity>)
}
