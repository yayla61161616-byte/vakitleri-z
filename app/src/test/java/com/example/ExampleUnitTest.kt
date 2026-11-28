package com.example

import com.example.domain.PrayerDay
import com.example.domain.PrayerTimeCalculator
import com.example.domain.PrayerType
import com.example.domain.QiblaCalculator
import com.example.domain.ReligiousDaysProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    private val sampleDay = PrayerDay(
        districtId = "9541",
        isoDate = "2026-11-27",
        miladiShort = "27.11.2026",
        miladiLong = "27 Kasım 2026 Cuma",
        hicriLong = "18 Cemaziyelahir 1448",
        imsak = "06:15",
        gunes = "07:45",
        ogle = "12:52",
        ikindi = "15:20",
        aksam = "17:42",
        yatsi = "19:05",
        sourceLabel = "Diyanet İşleri Başkanlığı",
        updatedAtMillis = 0L
    )

    @Test
    fun `countdown identifies active prayer and next prayer accurately`() {
        val nowMillis = PrayerTimeCalculator.parseTimeToMillis("2026-11-27", "13:30")!!
        val status = PrayerTimeCalculator.computeStatus(sampleDay, null, nowMillis)

        assertNotNull(status)
        assertEquals(PrayerType.OGLE, status!!.currentPrayer)
        assertEquals(PrayerType.IKINDI, status.nextPrayer)
        assertFalse(status.isNextTomorrow)
        assertEquals("01:50:00", PrayerTimeCalculator.formatCountdown(status.remainingMillis))
    }

    @Test
    fun `countdown rolls over to tomorrow imsak after yatsi`() {
        val tomorrow = sampleDay.copy(isoDate = "2026-11-28", imsak = "06:16")
        val nowMillis = PrayerTimeCalculator.parseTimeToMillis("2026-11-27", "21:16")!!
        val status = PrayerTimeCalculator.computeStatus(sampleDay, tomorrow, nowMillis)

        assertNotNull(status)
        assertEquals(PrayerType.YATSI, status!!.currentPrayer)
        assertEquals(PrayerType.IMSAK, status.nextPrayer)
        assertTrue(status.isNextTomorrow)
        assertEquals("06:16", status.nextPrayerTimeText)
    }

    @Test
    fun `short miladi date converts to iso date`() {
        assertEquals("2026-11-27", PrayerTimeCalculator.convertShortMiladiToIso("27.11.2026"))
    }

    @Test
    fun `turkish hours and minutes formatting is accurate`() {
        val oneHourFortyTwoMin = (1L * 60L + 42L) * 60_000L
        assertEquals("1 saat 42 dakika", PrayerTimeCalculator.formatHoursMinutesTurkish(oneHourFortyTwoMin))
        assertEquals("45 dakika", PrayerTimeCalculator.formatHoursMinutesTurkish(45L * 60_000L))
    }

    @Test
    fun `qibla bearing and distance for istanbul match expected southeast angle`() {
        val bearing = QiblaCalculator.calculateBearingDegrees(41.0082, 28.9784)
        val distanceKm = QiblaCalculator.calculateDistanceKm(41.0082, 28.9784)

        assertEquals(151.6, bearing, 0.5)
        assertEquals(2401.0, distanceKm, 25.0)
        assertEquals("Güneydoğu", QiblaCalculator.cardinalDirectionTurkish(bearing))
        assertEquals(-20f, QiblaCalculator.shortestAngleDiffDegrees(10f, 350f), 0.01f)
    }

    @Test
    fun `religious days relative label and date diff work properly`() {
        val baseMillis = PrayerTimeCalculator.parseTimeToMillis("2026-03-19", "12:00")!!
        assertEquals(0L, ReligiousDaysProvider.daysBetweenTodayAnd("2026-03-19", baseMillis))
        assertEquals(1L, ReligiousDaysProvider.daysBetweenTodayAnd("2026-03-20", baseMillis))
        assertEquals(-3L, ReligiousDaysProvider.daysBetweenTodayAnd("2026-03-16", baseMillis))
        assertEquals(7L, ReligiousDaysProvider.daysBetweenTodayAnd("2026-03-26", baseMillis))

        assertEquals("Bugün", ReligiousDaysProvider.relativeLabel(0L))
        assertEquals("Yarın", ReligiousDaysProvider.relativeLabel(1L))
        assertEquals("7 gün kaldı", ReligiousDaysProvider.relativeLabel(7L))
        assertEquals("Geçti", ReligiousDaysProvider.relativeLabel(-3L))
        assertTrue(ReligiousDaysProvider.allDays.any { it.year == 2026 } && ReligiousDaysProvider.allDays.any { it.year == 2027 })
    }

    @Test
    fun `tilt-compensated azimuth remains invariant under pitch and wrist bank`() {
        fun buildMatrix(yawDeg: Double, pitchDeg: Double, bankDeg: Double): FloatArray {
            val yaw = Math.toRadians(yawDeg)
            val pitch = Math.toRadians(pitchDeg)
            val bank = Math.toRadians(bankDeg)
            val sinY = kotlin.math.sin(yaw)
            val cosY = kotlin.math.cos(yaw)
            val cosP = kotlin.math.cos(pitch)
            val sinP = kotlin.math.sin(pitch)
            val cosB = kotlin.math.cos(bank)
            val sinB = kotlin.math.sin(bank)

            // R = R_z(-yaw) * R_x(pitch) * R_y(bank)
            val r0 = (cosY * cosB + sinY * sinP * sinB).toFloat()
            val r3 = (-sinY * cosB + cosY * sinP * sinB).toFloat()
            val r6 = (-cosP * sinB).toFloat()

            val r1 = (sinY * cosP).toFloat()
            val r4 = (cosY * cosP).toFloat()
            val r7 = sinP.toFloat()

            val r2 = (cosY * sinB - sinY * sinP * cosB).toFloat()
            val r5 = (-sinY * sinB - cosY * sinP * cosB).toFloat()
            val r8 = (cosP * cosB).toFloat()

            return floatArrayOf(
                r0, r1, r2,
                r3, r4, r5,
                r6, r7, r8
            )
        }

        // 60 deg tilted toward user with 15 deg wrist bank -> exact 90 deg East
        assertEquals(90.0, QiblaCalculator.azimuthFromRotationMatrix(buildMatrix(90.0, 60.0, 15.0)), 0.05)
        // Near-vertical (85 deg upright) -> uses back-of-phone fallback cleanly -> ~151.6 deg
        assertEquals(151.6, QiblaCalculator.azimuthFromRotationMatrix(buildMatrix(151.6, 85.0, 0.0)), 0.05)
    }

    @Test
    fun `coordinate location formats village and generates deterministic district id`() {
        val loc = com.example.domain.SelectedLocation(
            countryId = "geo",
            countryName = "Almanya",
            cityId = "geo",
            cityName = "Ludwigshafen am Rhein",
            districtId = com.example.domain.SelectedLocation.coordinateDistrictId(49.4908, 8.3740),
            districtName = "Oggersheim",
            latitude = 49.4908,
            longitude = 8.3740
        )
        assertTrue(loc.hasExactCoordinates())
        assertEquals("geo_49.4908_8.3740", loc.districtId)
        assertEquals("Ludwigshafen Am Rhein • Oggersheim", loc.formattedDisplay())
    }
}
