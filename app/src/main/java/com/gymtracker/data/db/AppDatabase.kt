package com.gymtracker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.gymtracker.data.db.daos.AppMetaDao
import com.gymtracker.data.db.entities.AppMetaEntity

/**
 * Lokalna baza aplikacji. W M0 zawiera tylko tabelę techniczną - tabele
 * treningowe (ćwiczenia, plany, sesje, pomiary) dochodzą w kolejnych krokach,
 * każda zmiana schematu z migracją i testem.
 */
@Database(
    entities = [AppMetaEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appMetaDao(): AppMetaDao

    companion object {
        const val NAME = "gym_tracker.db"
    }
}
