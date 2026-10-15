package com.gymtracker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.gymtracker.data.db.daos.AppMetaDao
import com.gymtracker.data.db.daos.ExerciseDao
import com.gymtracker.data.db.entities.AppMetaEntity
import com.gymtracker.data.db.entities.ExerciseEntity
import com.gymtracker.data.db.entities.ExerciseFtsEntity

/**
 * Lokalna baza aplikacji.
 *
 * Wersja 1: tylko tabela techniczna (M0).
 * Wersja 2: katalog ćwiczeń + indeks wyszukiwania FTS (M1).
 *
 * Każda zmiana schematu = podbicie wersji + migracja w [DatabaseMigrations]
 * (bez `fallbackToDestructiveMigration`) + wyeksportowany schemat w `app/schemas/`.
 */
@Database(
    entities = [
        AppMetaEntity::class,
        ExerciseEntity::class,
        ExerciseFtsEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appMetaDao(): AppMetaDao

    abstract fun exerciseDao(): ExerciseDao

    companion object {
        const val NAME = "gym_tracker.db"
    }
}
