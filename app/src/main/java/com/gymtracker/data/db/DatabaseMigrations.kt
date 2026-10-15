package com.gymtracker.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Zasady zmian schematu:
 *
 * 1. każda zmiana tabel = nowa wersja w [AppDatabase] + nowy obiekt [Migration],
 * 2. migrację dopisujemy do [ALL] (kolejność rosnąca),
 * 3. każda migracja ma test otwierający bazę w wersji N i podnoszący ją do N+1
 *    (`SchemaMigrationTest`). Room przy otwarciu sam porównuje wynik migracji
 *    z aktualnym schematem, więc rozjazd wychodzi w testach, a nie u użytkownika,
 * 4. nie używamy `fallbackToDestructiveMigration()` - dane treningowe
 *    użytkownika są cenne i nie mogą zniknąć po aktualizacji.
 *
 * SQL poniżej jest **kopią** tego, co Room generuje dla świeżej bazy
 * (`build/generated/ksp/.../AppDatabase_Impl.kt`). Przy każdej zmianie schematu
 * trzeba je zsynchronizować - inaczej Room zgłosi „Migration didn't properly
 * handle ...”, a test migracji to wyłapie.
 */
object DatabaseMigrations {

    /**
     * M1: katalog ćwiczeń.
     *
     * Dodaje tabelę `exercises`, jej indeksy oraz tabelę wyszukiwania `exercises_fts`
     * (FTS4 na zewnętrznej treści) razem z triggerami synchronizującymi indeks.
     */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `exercises` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`external_id` TEXT, " +
                    "`name` TEXT NOT NULL, " +
                    "`name_in_program` TEXT, " +
                    "`source_url` TEXT, " +
                    "`muscle_group` TEXT, " +
                    "`muscle_group_slug` TEXT, " +
                    "`secondary_muscles` TEXT, " +
                    "`equipment` TEXT, " +
                    "`mechanics` TEXT, " +
                    "`force_type` TEXT, " +
                    "`level` TEXT, " +
                    "`youtube_url` TEXT, " +
                    "`default_sets` INTEGER, " +
                    "`default_reps_min` INTEGER, " +
                    "`default_reps_max` INTEGER, " +
                    "`default_rest_s` INTEGER, " +
                    "`notes` TEXT, " +
                    "`is_favourite` INTEGER NOT NULL, " +
                    "`is_archived` INTEGER NOT NULL, " +
                    "`created_at` INTEGER NOT NULL, " +
                    "`updated_at` INTEGER NOT NULL)",
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `idx_exercises_ext` ON `exercises` (`external_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_exercises_muscle` ON `exercises` (`muscle_group`)")
            db.execSQL(
                "CREATE VIRTUAL TABLE IF NOT EXISTS `exercises_fts` USING FTS4(" +
                    "`name` TEXT NOT NULL, `muscle_group` TEXT, `equipment` TEXT, " +
                    "tokenize=unicode61, content=`exercises`)",
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_exercises_fts_BEFORE_UPDATE " +
                    "BEFORE UPDATE ON `exercises` BEGIN DELETE FROM `exercises_fts` " +
                    "WHERE `docid`=OLD.`rowid`; END",
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_exercises_fts_BEFORE_DELETE " +
                    "BEFORE DELETE ON `exercises` BEGIN DELETE FROM `exercises_fts` " +
                    "WHERE `docid`=OLD.`rowid`; END",
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_exercises_fts_AFTER_UPDATE " +
                    "AFTER UPDATE ON `exercises` BEGIN INSERT INTO `exercises_fts` " +
                    "(`docid`, `name`, `muscle_group`, `equipment`) VALUES " +
                    "(NEW.`rowid`, NEW.`name`, NEW.`muscle_group`, NEW.`equipment`); END",
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_exercises_fts_AFTER_INSERT " +
                    "AFTER INSERT ON `exercises` BEGIN INSERT INTO `exercises_fts` " +
                    "(`docid`, `name`, `muscle_group`, `equipment`) VALUES " +
                    "(NEW.`rowid`, NEW.`name`, NEW.`muscle_group`, NEW.`equipment`); END",
            )
        }
    }

    /**
     * M2: plany treningowe i sesje.
     *
     * Dodaje `plans`, `plan_days`, `plan_day_exercises` (plan + dni + ćwiczenia dni)
     * oraz `sessions` i `session_exercises` (rozpoczęty trening). Klucze obce
     * kasują kaskadowo to, co należy do planu, ale **nie** historię treningów
     * (`sessions.plan_id` / `plan_day_id` są `SET NULL`).
     */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `plans` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`external_id` TEXT, " +
                    "`name` TEXT NOT NULL, " +
                    "`description` TEXT, " +
                    "`source_url` TEXT, " +
                    "`author` TEXT, " +
                    "`is_active` INTEGER NOT NULL, " +
                    "`is_archived` INTEGER NOT NULL, " +
                    "`created_at` INTEGER NOT NULL, " +
                    "`updated_at` INTEGER NOT NULL)",
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `idx_plans_ext` ON `plans` (`external_id`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `plan_days` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`plan_id` INTEGER NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`day_of_week` INTEGER, " +
                    "`position` INTEGER NOT NULL, " +
                    "`note` TEXT, " +
                    "FOREIGN KEY(`plan_id`) REFERENCES `plans`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `idx_plan_days_position` " +
                    "ON `plan_days` (`plan_id`, `position`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `plan_day_exercises` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`plan_day_id` INTEGER NOT NULL, " +
                    "`exercise_id` INTEGER NOT NULL, " +
                    "`position` INTEGER NOT NULL, " +
                    "`target_sets` INTEGER, " +
                    "`target_reps_min` INTEGER, " +
                    "`target_reps_max` INTEGER, " +
                    "`target_reps_unit` TEXT NOT NULL, " +
                    "`target_weight_kg` REAL, " +
                    "`rest_seconds` INTEGER, " +
                    "`superset_group` INTEGER, " +
                    "`alternative_group` TEXT, " +
                    "`is_default_choice` INTEGER NOT NULL, " +
                    "`note` TEXT, " +
                    "FOREIGN KEY(`plan_day_id`) REFERENCES `plan_days`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`exercise_id`) REFERENCES `exercises`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `idx_pde_day` " +
                    "ON `plan_day_exercises` (`plan_day_id`, `position`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `idx_pde_exercise` " +
                    "ON `plan_day_exercises` (`exercise_id`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sessions` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`plan_id` INTEGER, " +
                    "`plan_day_id` INTEGER, " +
                    "`name` TEXT NOT NULL, " +
                    "`started_at` INTEGER NOT NULL, " +
                    "`finished_at` INTEGER, " +
                    "`status` TEXT NOT NULL, " +
                    "`note` TEXT, " +
                    "FOREIGN KEY(`plan_id`) REFERENCES `plans`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE SET NULL , " +
                    "FOREIGN KEY(`plan_day_id`) REFERENCES `plan_days`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE SET NULL )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_sessions_plan` ON `sessions` (`plan_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_sessions_day` ON `sessions` (`plan_day_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_sessions_status` ON `sessions` (`status`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `session_exercises` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`session_id` INTEGER NOT NULL, " +
                    "`exercise_id` INTEGER NOT NULL, " +
                    "`position` INTEGER NOT NULL, " +
                    "`target_sets` INTEGER, " +
                    "`target_reps_min` INTEGER, " +
                    "`target_reps_max` INTEGER, " +
                    "`target_reps_unit` TEXT NOT NULL, " +
                    "`rest_seconds` INTEGER, " +
                    "`superset_group` INTEGER, " +
                    "`alternative_group` TEXT, " +
                    "`is_default_choice` INTEGER NOT NULL, " +
                    "`note` TEXT, " +
                    "FOREIGN KEY(`session_id`) REFERENCES `sessions`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`exercise_id`) REFERENCES `exercises`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `idx_session_exercises_pos` " +
                    "ON `session_exercises` (`session_id`, `position`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `idx_session_exercises_exercise` " +
                    "ON `session_exercises` (`exercise_id`)",
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
