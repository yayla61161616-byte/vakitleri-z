package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class AladhanCalendarResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "status") val status: String,
    @Json(name = "data") val data: List<AladhanDayDto>
)

@JsonClass(generateAdapter = true)
data class AladhanDayDto(
    @Json(name = "timings") val timings: AladhanTimingsDto,
    @Json(name = "date") val date: AladhanDateDto
)

@JsonClass(generateAdapter = true)
data class AladhanTimingsDto(
    @Json(name = "Fajr") val fajr: String,
    @Json(name = "Sunrise") val sunrise: String,
    @Json(name = "Dhuhr") val dhuhr: String,
    @Json(name = "Asr") val asr: String,
    @Json(name = "Maghrib") val maghrib: String,
    @Json(name = "Isha") val isha: String
)

@JsonClass(generateAdapter = true)
data class AladhanDateDto(
    @Json(name = "gregorian") val gregorian: AladhanGregorianDto,
    @Json(name = "hijri") val hijri: AladhanHijriDto
)

@JsonClass(generateAdapter = true)
data class AladhanGregorianDto(
    @Json(name = "date") val date: String,
    @Json(name = "day") val day: String,
    @Json(name = "month") val month: AladhanMonthDto,
    @Json(name = "year") val year: String,
    @Json(name = "weekday") val weekday: AladhanWeekdayDto
)

@JsonClass(generateAdapter = true)
data class AladhanHijriDto(
    @Json(name = "day") val day: String,
    @Json(name = "month") val month: AladhanMonthDto,
    @Json(name = "year") val year: String
)

@JsonClass(generateAdapter = true)
data class AladhanMonthDto(
    @Json(name = "number") val number: Int,
    @Json(name = "en") val en: String
)

@JsonClass(generateAdapter = true)
data class AladhanWeekdayDto(
    @Json(name = "en") val en: String
)

interface AladhanApiService {
    @GET("v1/calendarByCity/{year}/{month}")
    suspend fun getMonthlyByCity(
        @Path("year") year: Int,
        @Path("month") month: Int,
        @Query("city") city: String,
        @Query("country") country: String,
        @Query("method") method: Int = 13 // Method 13 = Diyanet İşleri Başkanlığı, Turkey
    ): AladhanCalendarResponse
}
