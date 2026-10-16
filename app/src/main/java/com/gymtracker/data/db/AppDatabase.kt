package com.gymtracker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.gymtracker.data.db.daos.AppMetaDao
import com.gymtracker.data.db.daos.CalendarDao
import com.gymtracker.data.db.daos.ExerciseDao
import com.gymtracker.data.db.daos.PlanDao
import com.gymtracker.data.db.daos.SessionDao
import com.gymtracker.data.db.entities.AppMetaEntity
import com.gymtracker.data.db.entities.ExerciseEntity
import com.gymtracker.data.db.entities.ExerciseFtsEntity
import com.gymtracker.data.db.entities.PlanDayEntity
import com.gymtracker.data.db.entities.PlanEntity
import com.gymtracker.data.db.entities.PlanExerciseEntity
import com.gymtracker.data.db.entities.RecurrenceEntity
import com.gymtracker.data.db.entities.ScheduledSessionEntity
import com.gymtracker.data.db.entities.SessionEntity
import com.gymtracker.data.db.entities.SessionExerciseEntity

/**
 * Lokalna baza aplikacji.
 *
 * Wersja 1: tylko tabela techniczna (M0).
 * Wersja 2: katalog ćwiczeń + indeks wyszukiwania FTS (M1).
 * Wersja 3: plany treningowe (dni i ćwiczenia dni) oraz sesje treningowe (M2).
 * Wersja 4: kalendarz - reguły powtarzania i rozplanowane wpisy (M3).
 *
 * Każda zmiana schematu = podbicie wersji + migracja w [DatabaseMigrations]
 * (bez `fallbackToDestructiveMigration`) + wyeksportowany schemat w `app/schemas/`.
 */
@Database(
    entities = [
        AppMetaEntity::class,
        ExerciseEntity::class,
        ExerciseFtsEntity::class,
        PlanEntity::class,
        PlanDayEntity::class,
        PlanExerciseEntity::class,
        SessionEntity::class,
        SessionExerciseEntity::class,
        RecurrenceEntity::class,
        ScheduledSessionEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appMetaDao(): AppMetaDao

    abstract fun exerciseDao(): ExerciseDao

    abstract fun planDao(): PlanDao

    abstract fun sessionDao(): SessionDao

    abstract fun calendarDao(): CalendarDao

    companion object {
        const val NAME = "gym_tracker.db"
    }
}
