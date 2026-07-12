package com.salahlock.app.data.api

import retrofit2.http.GET
import retrofit2.http.Query

interface AladhanApiService {

    @GET("calendar")
    suspend fun getCalendar(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("method") method: Int,
        @Query("month") month: Int,
        @Query("year") year: Int,
        @Query("shafaq") shafaq: String = "general",
        @Query("tune") tune: String = "0,0,0,0,0,0,0,0,0",
        @Query("school") school: Int = 0 // 0 for Shafi, 1 for Hanafi
    ): AladhanResponse
}

data class AladhanResponse(
    val code: Int,
    val status: String,
    val data: List<AladhanData>
)

data class AladhanData(
    val timings: AladhanTimings,
    val date: AladhanDate
)

data class AladhanTimings(
    val Fajr: String,
    val Sunrise: String,
    val Dhuhr: String,
    val Asr: String,
    val Sunset: String,
    val Maghrib: String,
    val Isha: String,
    val Imsak: String,
    val Midnight: String,
    val Firstthird: String,
    val Lastthird: String
)

data class AladhanDate(
    val readable: String,
    val timestamp: String,
    val gregorian: GregorianDate,
    val hijri: HijriDate
)

data class GregorianDate(
    val date: String,
    val format: String,
    val day: String,
    val month: GregorianMonth,
    val year: String
)

data class GregorianMonth(
    val number: Int,
    val en: String
)

data class HijriDate(
    val date: String,
    val format: String,
    val day: String,
    val month: HijriMonth,
    val year: String,
    val holidays: List<String>
)

data class HijriMonth(
    val number: Int,
    val en: String,
    val ar: String
)
