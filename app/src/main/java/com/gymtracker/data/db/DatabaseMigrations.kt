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

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
