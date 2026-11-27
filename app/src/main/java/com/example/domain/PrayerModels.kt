package com.example.domain

enum class PrayerType(
    val key: String,
    val displayName: String,
    val notificationId: Int,
    val alarmBaseCode: Int
) {
    IMSAK("imsak", "İmsak", 1001, 100),
    GUNES("gunes", "Güneş", 1002, 200),
    OGLE("ogle", "Öğle", 1003, 300),
    IKINDI("ikindi", "İkindi", 1004, 400),
    AKSAM("aksam", "Akşam", 1005, 500),
    YATSI("yatsi", "Yatsı", 1006, 600);

    companion object {
        fun fromKey(key: String?): PrayerType? = entries.firstOrNull { it.key == key }
    }
}

enum class SoundType(val key: String) {
    SHORT_NOTIFICATION("short"),
    EZAN("ezan");

    companion object {
        fun fromKey(key: String?): SoundType = entries.firstOrNull { it.key == key } ?: SHORT_NOTIFICATION
    }
}

data class SelectedLocation(
    val countryId: String = "2",
    val countryName: String = "TÜRKİYE",
    val cityId: String = "539",
    val cityName: String = "İSTANBUL",
    val districtId: String = "9541",
    val districtName: String = "KADIKÖY"
) {
    fun formattedDisplay(): String {
        val cityPretty = cityName.toTurkishTitleCase()
        val districtPretty = districtName.toTurkishTitleCase()
        return if (cityPretty.equals(districtPretty, ignoreCase = true)) {
            cityPretty
        } else {
            "$cityPretty • $districtPretty"
        }
    }
}

data class PrayerDay(
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
    fun timeFor(type: PrayerType): String = when (type) {
        PrayerType.IMSAK -> imsak
        PrayerType.GUNES -> gunes
        PrayerType.OGLE -> ogle
        PrayerType.IKINDI -> ikindi
        PrayerType.AKSAM -> aksam
        PrayerType.YATSI -> yatsi
    }
}

data class CountryOption(
    val id: String,
    val name: String,
    val nameEn: String
)

data class CityOption(
    val id: String,
    val countryId: String,
    val name: String,
    val nameEn: String
)

data class DistrictOption(
    val id: String,
    val cityId: String,
    val name: String,
    val nameEn: String
)

data class NotificationPreferences(
    val soundType: SoundType = SoundType.SHORT_NOTIFICATION,
    val enabledPrayers: Map<PrayerType, Boolean> = PrayerType.entries.associateWith { true }
) {
    fun isEnabled(type: PrayerType): Boolean = enabledPrayers[type] ?: true
}

data class PrayerCountdownStatus(
    val currentPrayer: PrayerType,
    val nextPrayer: PrayerType,
    val nextPrayerTimeText: String,
    val isNextTomorrow: Boolean,
    val remainingMillis: Long,
    val intervalProgress: Float
)

fun String.toTurkishTitleCase(): String {
    val trLocale = java.util.Locale.forLanguageTag("tr-TR")
    return trim()
        .lowercase(trLocale)
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .joinToString(" ") { word ->
            word.replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase(trLocale) else ch.toString()
            }
        }
}
