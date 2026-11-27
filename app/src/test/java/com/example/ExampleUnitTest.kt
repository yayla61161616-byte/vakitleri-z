package com.example

import com.example.domain.PrayerDay
import com.example.domain.PrayerTimeCalculator
import com.example.domain.PrayerType
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
}
