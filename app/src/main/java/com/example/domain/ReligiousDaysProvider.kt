package com.example.domain

import java.util.Calendar
import java.util.concurrent.TimeUnit

enum class ReligiousDayCategory(val label: String) {
    KANDIL("Kandil Gecesi"),
    BAYRAM("Bayram"),
    MUBAREK("Mübarek Gün")
}

data class ReligiousDay(
    val id: String,
    val title: String,
    val isoDate: String,
    val formattedDateTr: String,
    val hijriLabel: String,
    val year: Int,
    val category: ReligiousDayCategory
)

object ReligiousDaysProvider {

    // Official Diyanet İşleri Başkanlığı 2026 & 2027 Religious Days Calendar
    val allDays: List<ReligiousDay> = listOf(
        // 2026
        ReligiousDay("2026_mirac", "Miraç Kandili", "2026-01-15", "15 Ocak 2026 Perşembe", "26 Recep 1447", 2026, ReligiousDayCategory.KANDIL),
        ReligiousDay("2026_berat", "Berat Kandili", "2026-02-02", "2 Şubat 2026 Pazartesi", "14 Şaban 1447", 2026, ReligiousDayCategory.KANDIL),
        ReligiousDay("2026_ramazan_baslangic", "Ramazan Başlangıcı (İlk Oruç)", "2026-02-19", "19 Şubat 2026 Perşembe", "1 Ramazan 1447", 2026, ReligiousDayCategory.MUBAREK),
        ReligiousDay("2026_kadir", "Kadir Gecesi", "2026-03-16", "16 Mart 2026 Pazartesi", "26 Ramazan 1447", 2026, ReligiousDayCategory.KANDIL),
        ReligiousDay("2026_ramazan_arefe", "Ramazan Bayramı Arefesi", "2026-03-19", "19 Mart 2026 Perşembe", "29 Ramazan 1447", 2026, ReligiousDayCategory.MUBAREK),
        ReligiousDay("2026_ramazan_1", "Ramazan Bayramı (1. Gün)", "2026-03-20", "20 Mart 2026 Cuma", "1 Şevval 1447", 2026, ReligiousDayCategory.BAYRAM),
        ReligiousDay("2026_ramazan_2", "Ramazan Bayramı (2. Gün)", "2026-03-21", "21 Mart 2026 Cumartesi", "2 Şevval 1447", 2026, ReligiousDayCategory.BAYRAM),
        ReligiousDay("2026_ramazan_3", "Ramazan Bayramı (3. Gün)", "2026-03-22", "22 Mart 2026 Pazar", "3 Şevval 1447", 2026, ReligiousDayCategory.BAYRAM),
        ReligiousDay("2026_kurban_arefe", "Kurban Bayramı Arefesi", "2026-05-26", "26 Mayıs 2026 Salı", "9 Zilhicce 1447", 2026, ReligiousDayCategory.MUBAREK),
        ReligiousDay("2026_kurban_1", "Kurban Bayramı (1. Gün)", "2026-05-27", "27 Mayıs 2026 Çarşamba", "10 Zilhicce 1447", 2026, ReligiousDayCategory.BAYRAM),
        ReligiousDay("2026_kurban_2", "Kurban Bayramı (2. Gün)", "2026-05-28", "28 Mayıs 2026 Perşembe", "11 Zilhicce 1447", 2026, ReligiousDayCategory.BAYRAM),
        ReligiousDay("2026_kurban_3", "Kurban Bayramı (3. Gün)", "2026-05-29", "29 Mayıs 2026 Cuma", "12 Zilhicce 1447", 2026, ReligiousDayCategory.BAYRAM),
        ReligiousDay("2026_kurban_4", "Kurban Bayramı (4. Gün)", "2026-05-30", "30 Mayıs 2026 Cumartesi", "13 Zilhicce 1447", 2026, ReligiousDayCategory.BAYRAM),
        ReligiousDay("2026_hicri_yilbasi", "Hicri Yılbaşı", "2026-06-16", "16 Haziran 2026 Salı", "1 Muharrem 1448", 2026, ReligiousDayCategory.MUBAREK),
        ReligiousDay("2026_asure", "Aşure Günü", "2026-06-25", "25 Haziran 2026 Perşembe", "10 Muharrem 1448", 2026, ReligiousDayCategory.MUBAREK),
        ReligiousDay("2026_mevlid", "Mevlid Kandili", "2026-08-24", "24 Ağustos 2026 Pazartesi", "11 Rebiülevvel 1448", 2026, ReligiousDayCategory.KANDIL),
        ReligiousDay("2026_uc_aylar_regaib", "Üç Ayların Başlangıcı ve Regaib Kandili", "2026-12-10", "10 Aralık 2026 Perşembe", "1 Recep 1448", 2026, ReligiousDayCategory.KANDIL),

        // 2027
        ReligiousDay("2027_mirac_1", "Miraç Kandili", "2027-01-04", "4 Ocak 2027 Pazartesi", "26 Recep 1448", 2027, ReligiousDayCategory.KANDIL),
        ReligiousDay("2027_berat", "Berat Kandili", "2027-01-22", "22 Ocak 2027 Cuma", "14 Şaban 1448", 2027, ReligiousDayCategory.KANDIL),
        ReligiousDay("2027_ramazan_baslangic", "Ramazan Başlangıcı (İlk Oruç)", "2027-02-08", "8 Şubat 2027 Pazartesi", "1 Ramazan 1448", 2027, ReligiousDayCategory.MUBAREK),
        ReligiousDay("2027_kadir", "Kadir Gecesi", "2027-03-05", "5 Mart 2027 Cuma", "26 Ramazan 1448", 2027, ReligiousDayCategory.KANDIL),
        ReligiousDay("2027_ramazan_arefe", "Ramazan Bayramı Arefesi", "2027-03-08", "8 Mart 2027 Pazartesi", "29 Ramazan 1448", 2027, ReligiousDayCategory.MUBAREK),
        ReligiousDay("2027_ramazan_1", "Ramazan Bayramı (1. Gün)", "2027-03-09", "9 Mart 2027 Salı", "1 Şevval 1448", 2027, ReligiousDayCategory.BAYRAM),
        ReligiousDay("2027_ramazan_2", "Ramazan Bayramı (2. Gün)", "2027-03-10", "10 Mart 2027 Çarşamba", "2 Şevval 1448", 2027, ReligiousDayCategory.BAYRAM),
        ReligiousDay("2027_ramazan_3", "Ramazan Bayramı (3. Gün)", "2027-03-11", "11 Mart 2027 Perşembe", "3 Şevval 1448", 2027, ReligiousDayCategory.BAYRAM),
        ReligiousDay("2027_kurban_arefe", "Kurban Bayramı Arefesi", "2027-05-15", "15 Mayıs 2027 Cumartesi", "9 Zilhicce 1448", 2027, ReligiousDayCategory.MUBAREK),
        ReligiousDay("2027_kurban_1", "Kurban Bayramı (1. Gün)", "2027-05-16", "16 Mayıs 2027 Pazar", "10 Zilhicce 1448", 2027, ReligiousDayCategory.BAYRAM),
        ReligiousDay("2027_kurban_2", "Kurban Bayramı (2. Gün)", "2027-05-17", "17 Mayıs 2027 Pazartesi", "11 Zilhicce 1448", 2027, ReligiousDayCategory.BAYRAM),
        ReligiousDay("2027_kurban_3", "Kurban Bayramı (3. Gün)", "2027-05-18", "18 Mayıs 2027 Salı", "12 Zilhicce 1448", 2027, ReligiousDayCategory.BAYRAM),
        ReligiousDay("2027_kurban_4", "Kurban Bayramı (4. Gün)", "2027-05-19", "19 Mayıs 2027 Çarşamba", "13 Zilhicce 1448", 2027, ReligiousDayCategory.BAYRAM),
        ReligiousDay("2027_hicri_yilbasi", "Hicri Yılbaşı", "2027-06-06", "6 Haziran 2027 Pazar", "1 Muharrem 1449", 2027, ReligiousDayCategory.MUBAREK),
        ReligiousDay("2027_asure", "Aşure Günü", "2027-06-15", "15 Haziran 2027 Salı", "10 Muharrem 1449", 2027, ReligiousDayCategory.MUBAREK),
        ReligiousDay("2027_mevlid", "Mevlid Kandili", "2027-08-13", "13 Ağustos 2027 Cuma", "11 Rebiülevvel 1449", 2027, ReligiousDayCategory.KANDIL),
        ReligiousDay("2027_uc_aylar", "Üç Ayların Başlangıcı", "2027-11-29", "29 Kasım 2027 Pazartesi", "1 Recep 1449", 2027, ReligiousDayCategory.MUBAREK),
        ReligiousDay("2027_regaib", "Regaib Kandili", "2027-12-02", "2 Aralık 2027 Perşembe", "4 Recep 1449", 2027, ReligiousDayCategory.KANDIL),
        ReligiousDay("2027_mirac_2", "Miraç Kandili", "2027-12-24", "24 Aralık 2027 Cuma", "26 Recep 1449", 2027, ReligiousDayCategory.KANDIL)
    )

    fun daysBetweenTodayAnd(targetIsoDate: String, nowMillis: Long = System.currentTimeMillis()): Long? {
        val parts = targetIsoDate.split("-")
        if (parts.size != 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = (parts[1].toIntOrNull() ?: return null) - 1
        val d = parts[2].toIntOrNull() ?: return null

        val todayMidnight = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val targetMidnight = Calendar.getInstance().apply {
            set(Calendar.YEAR, y)
            set(Calendar.MONTH, m)
            set(Calendar.DAY_OF_MONTH, d)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        return kotlin.math.round((targetMidnight - todayMidnight) / 86_400_000.0).toLong()
    }

    fun relativeLabel(daysDiff: Long): String = when {
        daysDiff == 0L -> "Bugün"
        daysDiff == 1L -> "Yarın"
        daysDiff > 1L -> "$daysDiff gün kaldı"
        else -> "Geçti"
    }
}
