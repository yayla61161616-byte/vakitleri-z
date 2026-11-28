package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class NominatimPlaceDto(
    @Json(name = "place_id") val placeId: Long? = null,
    @Json(name = "lat") val lat: String,
    @Json(name = "lon") val lon: String,
    @Json(name = "name") val name: String? = null,
    @Json(name = "display_name") val displayName: String,
    @Json(name = "type") val type: String? = null,
    @Json(name = "address") val address: NominatimAddressDto? = null
)

@JsonClass(generateAdapter = true)
data class NominatimAddressDto(
    @Json(name = "village") val village: String? = null,
    @Json(name = "hamlet") val hamlet: String? = null,
    @Json(name = "suburb") val suburb: String? = null,
    @Json(name = "quarter") val quarter: String? = null,
    @Json(name = "neighbourhood") val neighbourhood: String? = null,
    @Json(name = "city_district") val cityDistrict: String? = null,
    @Json(name = "town") val town: String? = null,
    @Json(name = "city") val city: String? = null,
    @Json(name = "municipality") val municipality: String? = null,
    @Json(name = "county") val county: String? = null,
    @Json(name = "state") val state: String? = null,
    @Json(name = "country") val country: String? = null
)

interface NominatimApiService {
    @GET("search")
    suspend fun searchPlaces(
        @Query("q") query: String,
        @Query("format") format: String = "jsonv2",
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("limit") limit: Int = 12,
        @Header("User-Agent") userAgent: String = "EzanVaktimAndroid/1.0",
        @Header("Accept-Language") acceptLanguage: String = "tr,de,en"
    ): List<NominatimPlaceDto>
}
