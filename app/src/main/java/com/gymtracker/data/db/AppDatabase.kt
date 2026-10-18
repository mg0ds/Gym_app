package com.gymtracker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.gymtracker.data.backup.BackupDao
import com.gymtracker.data.db.daos.AppMetaDao
import com.gymtracker.data.db.daos.BodyDao
import com.gymtracker.data.db.daos.CalendarDao
import com.gymtracker.data.db.daos.ExerciseDao
import com.gymtracker.data.db.daos.MediaDao
import com.gymtracker.data.db.daos.PlanDao
import com.gymtracker.data.db.daos.RestDao
import com.gymtracker.data.db.daos.SeedImportDao
import com.gymtracker.data.db.daos.SessionDao
import com.gymtracker.data.db.daos.SetLogDao
import com.gymtracker.data.db.daos.StatsDao
import com.gymtracker.data.db.entities.AppMetaEntity
import com.gymtracker.data.db.entities.BodyEntryEntity
import com.gymtracker.data.db.entities.ExerciseEntity
import com.gymtracker.data.db.entities.ExerciseFtsEntity
import com.gymtracker.data.db.entities.ExerciseMediaEntity
import com.gymtracker.data.db.entities.MetricTypeEntity
import com.gymtracker.data.db.entities.PlanDayEntity
import com.gymtracker.data.db.entities.PlanEntity
import com.gymtracker.data.db.entities.PlanExerciseEntity
import com.gymtracker.data.db.entities.RecurrenceEntity
import com.gymtracker.data.db.entities.RestIntervalEntity
import com.gymtracker.data.db.entities.ScheduledSessionEntity
import com.gymtracker.data.db.entities.SeedImportEntity
import com.gymtracker.data.db.entities.SessionEntity
import com.gymtracker.data.db.entities.SessionExerciseEntity
import com.gymtracker.data.db.entities.SetLogEntity

/**
 * Lokalna baza aplikacji.
 *
 * Wersja 1: tylko tabela techniczna (M0).
 * Wersja 2: katalog ćwiczeń + indeks wyszukiwania FTS (M1).
 * Wersja 3: plany treningowe (dni i ćwiczenia dni) oraz sesje treningowe (M2).
 * Wersja 4: kalendarz - reguły powtarzania i rozplanowane wpisy (M3).
 * Wersja 5: logowanie serii (`set_logs`) i pauza treningu w `sessions` (M4).
 * Wersja 6: przerwy między seriami (`rest_intervals`) z celem i powodem
 * zakończenia (M5).
 * Wersja 7: kroki wykonania ćwiczenia (`exercises.instructions`) i załączniki
 * z galerii (`exercise_media`) - wskazania do plików, nie ich kopie (M7).
 * Wersja 8: karta postaci - wskaźniki (`metric_types`) i ich pomiary
 * (`body_entries`) (M8).
 * Wersja 9: dziennik importów katalogów (`seed_imports`) - skąd przyszły
 * dane i co z tego wyszło, razem z plikami odrzuconymi (M9).
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
        SetLogEntity::class,
        RecurrenceEntity::class,
        ScheduledSessionEntity::class,
        RestIntervalEntity::class,
        ExerciseMediaEntity::class,
        MetricTypeEntity::class,
        BodyEntryEntity::class,
        SeedImportEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appMetaDao(): AppMetaDao

    abstract fun exerciseDao(): ExerciseDao

    abstract fun planDao(): PlanDao

    abstract fun sessionDao(): SessionDao

    abstract fun setLogDao(): SetLogDao

    abstract fun restDao(): RestDao

    abstract fun calendarDao(): CalendarDao

    abstract fun statsDao(): StatsDao

    abstract fun mediaDao(): MediaDao

    abstract fun bodyDao(): BodyDao

    abstract fun seedImportDao(): SeedImportDao

    abstract fun backupDao(): BackupDao

    companion object {
        const val NAME = "gym_tracker.db"
    }
}
