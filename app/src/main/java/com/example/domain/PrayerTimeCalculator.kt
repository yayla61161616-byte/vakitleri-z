package com.example.domain

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object PrayerTimeCalculator {

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun todayIsoDate(nowMillis: Long = System.currentTimeMillis()): String {
        return isoFormat.format(Date(nowMillis))
    }

    fun tomorrowIsoDate(nowMillis: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, 1)
        }
        return isoFormat.format(cal.time)
    }

    fun isoDateOffset(daysOffset: Int, nowMillis: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, daysOffset)
        }
        return isoFormat.format(cal.time)
    }

    fun parseTimeToMillis(isoDate: String, hhMm: String): Long? {
        val cleanTime = hhMm.trim().take(5)
        val dateParts = isoDate.split("-")
        val timeParts = cleanTime.split(":")
        if (dateParts.size != 3 || timeParts.size != 2) return null

        val year = dateParts[0].toIntOrNull() ?: return null
        val month = (dateParts[1].toIntOrNull() ?: return null) - 1
        val day = dateParts[2].toIntOrNull() ?: return null
        val hour = timeParts[0].toIntOrNull() ?: return null
        val minute = timeParts[1].toIntOrNull() ?: return null

        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun computeStatus(
        today: PrayerDay,
        tomorrow: PrayerDay?,
        nowMillis: Long = System.currentTimeMillis()
    ): PrayerCountdownStatus? {
        val entriesToday = PrayerType.entries.mapNotNull { type ->
            val millis = parseTimeToMillis(today.isoDate, today.timeFor(type))
            millis?.let { Triple(type, today.timeFor(type).take(5), it) }
        }
        if (entriesToday.size < 6) return null

        val nextIdx = entriesToday.indexOfFirst { it.third > nowMillis }

        return if (nextIdx != -1) {
            val next = entriesToday[nextIdx]
            val current = if (nextIdx == 0) PrayerType.YATSI else entriesToday[nextIdx - 1].first
            val prevMillis = if (nextIdx == 0) {
                // Before today's Imsak: previous prayer was yesterday's Yatsi (estimate -1 day from today's Yatsi)
                entriesToday.last().third - 24L * 60L * 60L * 1000L
            } else {
                entriesToday[nextIdx - 1].third
            }
            val span = (next.third - prevMillis).coerceAtLeast(1L)
            val elapsed = (nowMillis - prevMillis).coerceIn(0L, span)
            PrayerCountdownStatus(
                currentPrayer = current,
                nextPrayer = next.first,
                nextPrayerTimeText = next.second,
                nextPrayerTriggerMillis = next.third,
                isNextTomorrow = false,
                remainingMillis = (next.third - nowMillis).coerceAtLeast(0L),
                intervalProgress = elapsed.toFloat() / span.toFloat()
            )
        } else {
            // After today's Yatsi -> count down to tomorrow's Imsak
            val yatsiMillis = entriesToday.last().third
            val tomorrowImsakText = tomorrow?.imsak?.take(5) ?: today.imsak.take(5)
            val tomorrowIso = tomorrow?.isoDate ?: tomorrowIsoDate(nowMillis)
            val tomorrowImsakMillis = parseTimeToMillis(tomorrowIso, tomorrowImsakText)
                ?: (yatsiMillis + 10L * 60L * 60L * 1000L)

            val span = (tomorrowImsakMillis - yatsiMillis).coerceAtLeast(1L)
            val elapsed = (nowMillis - yatsiMillis).coerceIn(0L, span)

            PrayerCountdownStatus(
                currentPrayer = PrayerType.YATSI,
                nextPrayer = PrayerType.IMSAK,
                nextPrayerTimeText = tomorrowImsakText,
                nextPrayerTriggerMillis = tomorrowImsakMillis,
                isNextTomorrow = true,
                remainingMillis = (tomorrowImsakMillis - nowMillis).coerceAtLeast(0L),
                intervalProgress = elapsed.toFloat() / span.toFloat()
            )
        }
    }

    fun formatCountdown(remainingMillis: Long): String {
        val totalSeconds = (remainingMillis.coerceAtLeast(0L) / 1000L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun formatHoursMinutesTurkish(remainingMillis: Long): String {
        val totalMinutes = ((remainingMillis.coerceAtLeast(0L) + 59_999L) / 60_000L)
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return when {
            hours > 0L && minutes > 0L -> "$hours saat $minutes dakika"
            hours > 0L -> "$hours saat"
            minutes > 0L -> "$minutes dakika"
            else -> "1 dakikadan az"
        }
    }

    fun convertShortMiladiToIso(miladiShort: String): String? {
        // EzanVakti format: "27.11.2026"
        val parts = miladiShort.trim().split(".")
        if (parts.size != 3) return null
        val day = parts[0].padStart(2, '0')
        val month = parts[1].padStart(2, '0')
        val year = parts[2]
        if (year.length != 4) return null
        return "$year-$month-$day"
    }
}
