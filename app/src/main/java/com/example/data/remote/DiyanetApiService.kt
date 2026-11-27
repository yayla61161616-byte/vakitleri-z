package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path

@JsonClass(generateAdapter = true)
data class DiyanetCountryDto(
    @Json(name = "UlkeID") val ulkeId: String,
    @Json(name = "UlkeAdi") val ulkeAdi: String,
    @Json(name = "UlkeAdiEn") val ulkeAdiEn: String? = null
)

@JsonClass(generateAdapter = true)
data class DiyanetCityDto(
    @Json(name = "SehirID") val sehirId: String,
    @Json(name = "SehirAdi") val sehirAdi: String,
    @Json(name = "SehirAdiEn") val sehirAdiEn: String? = null
)

@JsonClass(generateAdapter = true)
data class DiyanetDistrictDto(
    @Json(name = "IlceID") val ilceId: String,
    @Json(name = "IlceAdi") val ilceAdi: String,
    @Json(name = "IlceAdiEn") val ilceAdiEn: String? = null
)

@JsonClass(generateAdapter = true)
data class DiyanetPrayerDayDto(
    @Json(name = "MiladiTarihKisa") val miladiTarihKisa: String,
    @Json(name = "MiladiTarihUzun") val miladiTarihUzun: String? = null,
    @Json(name = "HicriTarihUzun") val hicriTarihUzun: String? = null,
    @Json(name = "Imsak") val imsak: String,
    @Json(name = "Gunes") val gunes: String,
    @Json(name = "Ogle") val ogle: String,
    @Json(name = "Ikindi") val ikindi: String,
    @Json(name = "Aksam") val aksam: String,
    @Json(name = "Yatsi") val yatsi: String
)

interface DiyanetApiService {
    @GET("ulkeler")
    suspend fun getCountries(): List<DiyanetCountryDto>

    @GET("sehirler/{ulkeId}")
    suspend fun getCities(@Path("ulkeId") ulkeId: String): List<DiyanetCityDto>

    @GET("ilceler/{sehirId}")
    suspend fun getDistricts(@Path("sehirId") sehirId: String): List<DiyanetDistrictDto>

    @GET("vakitler/{ilceId}")
    suspend fun getPrayerTimes(@Path("ilceId") ilceId: String): List<DiyanetPrayerDayDto>
}
