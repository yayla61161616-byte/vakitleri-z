package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.CityOption
import com.example.domain.CountryOption
import com.example.domain.DistrictOption
import com.example.domain.PrayerDay

@Entity(
    tableName = "prayer_days",
    primaryKeys = ["districtId", "isoDate"]
)
data class PrayerDayEntity(
    val districtId: String,
    val isoDate: String,
    val miladiShort: String,
    val miladiLong: String,
    val hicriLong: String,
    val imsak: String,
    val gunes: String,
    val ogle: String,
    val ikindi: String,
    val aksam: String,
    val yatsi: String,
    val sourceLabel: String,
    val updatedAtMillis: Long
) {
    fun toDomain(): PrayerDay = PrayerDay(
        districtId = districtId,
        isoDate = isoDate,
        miladiShort = miladiShort,
        miladiLong = miladiLong,
        hicriLong = hicriLong,
        imsak = imsak,
        gunes = gunes,
        ogle = ogle,
        ikindi = ikindi,
        aksam = aksam,
        yatsi = yatsi,
        sourceLabel = sourceLabel,
        updatedAtMillis = updatedAtMillis
    )
}

@Entity(tableName = "countries")
data class CountryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val nameEn: String
) {
    fun toDomain(): CountryOption = CountryOption(id = id, name = name, nameEn = nameEn)
}

@Entity(tableName = "cities")
data class CityEntity(
    @PrimaryKey val id: String,
    val countryId: String,
    val name: String,
    val nameEn: String
) {
    fun toDomain(): CityOption = CityOption(id = id, countryId = countryId, name = name, nameEn = nameEn)
}

@Entity(tableName = "districts")
data class DistrictEntity(
    @PrimaryKey val id: String,
    val cityId: String,
    val name: String,
    val nameEn: String
) {
    fun toDomain(): DistrictOption = DistrictOption(id = id, cityId = cityId, name = name, nameEn = nameEn)
}
