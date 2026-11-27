package com.example.data.local

object SeedLocations {
    val countries = listOf(
        CountryEntity("2", "TÜRKİYE", "TURKEY"),
        CountryEntity("13", "ALMANYA", "GERMANY"),
        CountryEntity("11", "HOLLANDA", "NETHERLANDS"),
        CountryEntity("21", "FRANSA", "FRANCE"),
        CountryEntity("15", "İNGİLTERE", "UNITED KINGDOM"),
        CountryEntity("33", "ABD", "UNITED STATES"),
        CountryEntity("5", "AZERBAYCAN", "AZERBAIJAN"),
        CountryEntity("16", "BELÇİKA", "BELGIUM"),
        CountryEntity("4", "AVUSTURYA", "AUSTRIA"),
        CountryEntity("64", "SUUDİ ARABİSTAN", "SAUDI ARABIA")
    )

    val turkeyCities = listOf(
        CityEntity("500", "2", "ADANA", "ADANA"),
        CityEntity("506", "2", "ANKARA", "ANKARA"),
        CityEntity("507", "2", "ANTALYA", "ANTALYA"),
        CityEntity("520", "2", "BURSA", "BURSA"),
        CityEntity("529", "2", "ESKİŞEHİR", "ESKISEHIR"),
        CityEntity("531", "2", "GAZİANTEP", "GAZIANTEP"),
        CityEntity("539", "2", "İSTANBUL", "ISTANBUL"),
        CityEntity("540", "2", "İZMİR", "IZMIR"),
        CityEntity("547", "2", "KAYSERİ", "KAYSERI"),
        CityEntity("552", "2", "KONYA", "KONYA"),
        CityEntity("565", "2", "SAMSUN", "SAMSUN"),
        CityEntity("573", "2", "TRABZON", "TRABZON")
    )

    val popularDistricts = listOf(
        DistrictEntity("9541", "539", "KADIKÖY", "KADIKOY"),
        DistrictEntity("9535", "539", "FATİH", "FATIH"),
        DistrictEntity("9552", "539", "ÜSKÜDAR", "USKUDAR"),
        DistrictEntity("9528", "539", "BEŞİKTAŞ", "BESIKTAS"),
        DistrictEntity("9206", "506", "ÇANKAYA", "CANKAYA"),
        DistrictEntity("9210", "506", "KEÇİÖREN", "KECIOREN"),
        DistrictEntity("9560", "540", "KONAK", "KONAK"),
        DistrictEntity("9559", "540", "KARŞIYAKA", "KARSIYAKA"),
        DistrictEntity("9335", "520", "OSMANGAZİ", "OSMANGAZI"),
        DistrictEntity("9225", "507", "MURATPAŞA", "MURATPASA"),
        DistrictEntity("9676", "552", "SELÇUKLU", "SELCUKLU"),
        DistrictEntity("9146", "500", "SEYHAN", "SEYHAN"),
        DistrictEntity("9881", "573", "ORTAHİSAR", "ORTAHISAR")
    )
}
