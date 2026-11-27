package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PrayerDayEntity::class,
        CountryEntity::class,
        CityEntity::class,
        DistrictEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PrayerDatabase : RoomDatabase() {
    abstract fun prayerDao(): PrayerDao

    companion object {
        @Volatile
        private var INSTANCE: PrayerDatabase? = null

        fun getInstance(context: Context): PrayerDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PrayerDatabase::class.java,
                    "ezan_vaktim_db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
