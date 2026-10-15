package com.gymtracker.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gymtracker.data.db.entities.ExerciseEntity
import com.gymtracker.data.db.entities.PlanDayEntity
import com.gymtracker.data.db.entities.PlanEntity
import com.gymtracker.data.db.entities.PlanExerciseEntity
import com.gymtracker.data.search.ExerciseSearchQuery
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Test migracji schematu: zakłada bazę w wersji 1 (taką, jaką miała użytkownika
 * aplikacja z M0), otwiera ją kodem, który zna wersję 2, i sprawdza, że:
 *
 *  * dane użytkownika przetrwały,
 *  * Room zaakceptował schemat po migracji (sam porównuje go z oczekiwanym),
 *  * katalog ćwiczeń działa, łącznie z indeksem wyszukiwania założonym w migracji.
 *
 * Gdyby SQL w [DatabaseMigrations] rozjechał się z tym, co generuje Room
 * (np. brakujący `NOT NULL` czy inny tokenizer FTS), ten test to wyłapie.
 */
@RunWith(RobolectricTestRunner::class)
class SchemaMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        context.deleteDatabase(DB_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun `migracja 1 do 3 zachowuje dane i dodaje katalog oraz plany`() = runTest {
        createVersion1Database()

        val database = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .addMigrations(*DatabaseMigrations.ALL)
            .build()

        try {
            // Dane z wersji 1 są nietknięte.
            assertEquals("1", database.appMetaDao().get("catalog_version"))

            // Katalog ćwiczeń działa (przy otwarciu Room zweryfikował schemat v3).
            val exerciseId = database.exerciseDao().insert(
                ExerciseEntity(
                    name = "Wyciskanie sztangą leżąc",
                    muscleGroup = "Chest",
                    createdAtEpochMs = 2_000L,
                    updatedAtEpochMs = 2_000L,
                ),
            )

            // Indeks FTS założony przez migrację też działa (polskie znaki).
            val found = database.exerciseDao()
                .observeExercises(
                    ftsQuery = ExerciseSearchQuery.build("sztanga"),
                    muscleGroup = null,
                    favouritesOnly = false,
                )
                .first()

            assertEquals(listOf("Wyciskanie sztangą leżąc"), found.map { it.name })

            // Tabele planów z migracji 2→3 przyjmują dane i wiążą je kluczami.
            val planId = database.planDao().insertPlan(
                PlanEntity(
                    name = "Mój plan",
                    createdAtEpochMs = 2_000L,
                    updatedAtEpochMs = 2_000L,
                ),
            )
            val dayId = database.planDao().insertDay(
                PlanDayEntity(planId = planId, name = "Dzień A", position = 1, dayOfWeek = 0),
            )
            database.planDao().insertDayExercise(
                PlanExerciseEntity(
                    planDayId = dayId,
                    exerciseId = exerciseId,
                    position = 1,
                    targetSets = 5,
                    targetRepsMin = 5,
                    targetRepsMax = 5,
                ),
            )

            val day = requireNotNull(database.planDao().getDay(dayId))
            assertEquals(planId, day.planId)

            // Kaskada: usunięcie planu zabiera dni i ćwiczenia dni.
            database.planDao().deletePlan(planId)
            assertNull(database.planDao().getDay(dayId))
            assertTrue(database.planDao().getDayExercises(dayId).isEmpty())
        } finally {
            database.close()
        }
    }

    /** Baza w wersji 1: tylko tabela techniczna z M0. */
    private fun createVersion1Database() {
        val db = context.openOrCreateDatabase(DB_NAME, Context.MODE_PRIVATE, null)
        try {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `app_meta` (" +
                    "`meta_key` TEXT NOT NULL, `meta_value` TEXT NOT NULL, " +
                    "`updated_at` INTEGER NOT NULL, PRIMARY KEY(`meta_key`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS room_master_table " +
                    "(id INTEGER PRIMARY KEY,identity_hash TEXT)",
            )
            db.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) " +
                    "VALUES(42, '$V1_IDENTITY_HASH')",
            )
            db.execSQL(
                "INSERT INTO app_meta (meta_key, meta_value, updated_at) " +
                    "VALUES ('catalog_version', '1', 1000)",
            )
            db.version = 1
        } finally {
            db.close()
        }
    }

    private companion object {
        const val DB_NAME = "schema-migration-test.db"

        /** `identityHash` wersji 1 z `app/schemas/com.gymtracker.data.db.AppDatabase/1.json`. */
        const val V1_IDENTITY_HASH = "2adcf5c0241c3427b90b28bff6bbdcb8"
    }
}
