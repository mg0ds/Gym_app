package com.gymtracker.data.db

import androidx.room.migration.Migration

/**
 * Zasady zmian schematu:
 *
 * 1. każda zmiana tabel = nowa wersja w [AppDatabase] + nowy obiekt [Migration],
 * 2. migrację dopisujemy do [ALL] (kolejność rosnąca),
 * 3. do każdej migracji piszemy test w `androidTest` (MigrationTestHelper)
 *    na podstawie wyeksportowanego schematu z `app/schemas/`,
 * 4. nie używamy `fallbackToDestructiveMigration()` - dane treningowe
 *    użytkownika są cenne i nie mogą zniknąć po aktualizacji.
 */
object DatabaseMigrations {

    val ALL: Array<Migration> = emptyArray()
}
