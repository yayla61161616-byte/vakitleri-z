package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.PrayerDay
import com.example.domain.PrayerTimeCalculator
import com.example.domain.PrayerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Ezan Vaktim", appName)
    }

    @Test
    fun `countdown identifies active prayer and next prayer accurately`() {
        val today = PrayerDay(
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

        val nowMillis = PrayerTimeCalculator.parseTimeToMillis("2026-11-27", "13:30")!!
        val status = PrayerTimeCalculator.computeStatus(today, null, nowMillis)

        assertNotNull(status)
        assertEquals(PrayerType.OGLE, status!!.currentPrayer)
        assertEquals(PrayerType.IKINDI, status.nextPrayer)
        assertFalse(status.isNextTomorrow)
        assertEquals("01:50:00", PrayerTimeCalculator.formatCountdown(status.remainingMillis))
    }

    @Test
    fun `countdown rolls over to tomorrow imsak after yatsi`() {
        val today = PrayerDay(
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
        val tomorrow = today.copy(isoDate = "2026-11-28", imsak = "06:16")

        val nowMillis = PrayerTimeCalculator.parseTimeToMillis("2026-11-27", "21:16")!!
        val status = PrayerTimeCalculator.computeStatus(today, tomorrow, nowMillis)

        assertNotNull(status)
        assertEquals(PrayerType.YATSI, status!!.currentPrayer)
        assertEquals(PrayerType.IMSAK, status.nextPrayer)
        assertTrue(status.isNextTomorrow)
        assertEquals("06:16", status.nextPrayerTimeText)
    }
}
