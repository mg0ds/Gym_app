package com.gymtracker.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gymtracker.data.db.entities.ExerciseEntity
import com.gymtracker.data.db.entities.PlanDayEntity
import com.gymtracker.data.db.entities.PlanEntity
import com.gymtracker.data.db.entities.PlanExerciseEntity
import com.gymtracker.data.db.entities.RecurrenceEntity
import com.gymtracker.data.db.entities.RestIntervalEntity
import com.gymtracker.data.db.entities.ScheduledSessionEntity
import com.gymtracker.data.db.entities.SessionEntity
import com.gymtracker.data.db.entities.SessionExerciseEntity
import com.gymtracker.data.db.entities.SetLogEntity
import com.gymtracker.data.search.ExerciseSearchQuery
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Test migracji schematu: zakłada bazę w wersji 1 (taką, jaką miała użytkownika
 * aplikacja z M0), otwiera ją kodem, który zna wersję 6, i sprawdza, że:
 *
 *  * dane użytkownika przetrwały,
 *  * Room zaakceptował schemat po migracji (sam porównuje go z oczekiwanym),
 *  * katalog ćwiczeń działa, łącznie z indeksem wyszukiwania założonym w migracji,
 *  * kalendarz z migracji 3→4 przyjmuje reguły i wpisy,
 *  * logowanie serii i pauza z migracji 4→5 działają na starych sesjach,
 *  * przerwy z migracji 5→6 da się zapisać i domknąć, a baza pilnuje słownika
 *    powodów zakończenia.
 *
 * Gdyby SQL w [DatabaseMigrations] rozjechał się z tym, co generuje Room
 * (np. brakujący `NOT NULL` czy `DEFAULT` przy dodanej kolumnie), ten test
 * to wyłapie.
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
    fun `migracja 1 do 6 zachowuje dane i dodaje katalog, plany, kalendarz, serie i przerwy`() = runTest {
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

            // Kalendarz z migracji 3→4: reguła i wpis z rotacją dnia planu.
            val recurrenceId = database.calendarDao().insertRecurrence(
                RecurrenceEntity(
                    planId = planId,
                    planDayId = dayId,
                    startDate = "2026-10-01",
                    endDate = "2026-12-31",
                    intervalDays = 3,
                    weekdaysMask = null,
                    createdAtEpochMs = 2_000L,
                ),
            )
            database.calendarDao().insertScheduled(
                listOf(
                    ScheduledSessionEntity(
                        date = "2026-10-01",
                        planId = planId,
                        planDayId = dayId,
                        recurrenceId = recurrenceId,
                        title = "Dzień A",
                        status = ScheduledSessionEntity.STATUS_PLANNED,
                        createdAtEpochMs = 2_000L,
                    ),
                ),
            )
            val scheduled = database.calendarDao().getScheduledOn("2026-10-01").single()
            assertEquals(dayId, scheduled.planDayId)
            assertEquals(recurrenceId, scheduled.recurrenceId)

            // Migracja 4→5: logowanie serii. Sesja z M2 (jeszcze bez kolumn
            // pauzy) dostaje zero pauz, a serie da się dopisać i odczytać.
            val sessionId = database.sessionDao().insertSession(
                SessionEntity(
                    planId = planId,
                    planDayId = dayId,
                    name = "Dzień A",
                    startedAtEpochMs = 3_000L,
                    status = SessionEntity.STATUS_ACTIVE,
                ),
            )
            val legacySession = requireNotNull(database.sessionDao().getSession(sessionId))
            assertEquals("stara sesja startuje z zerem pauz", 0L, legacySession.pausedTotalMs)
            assertNull(legacySession.pausedAtEpochMs)

            val sessionExerciseId = database.sessionDao().insertSessionExercises(
                listOf(
                    SessionExerciseEntity(
                        sessionId = sessionId,
                        exerciseId = exerciseId,
                        position = 1,
                        targetSets = 5,
                        targetRepsMin = 5,
                        targetRepsMax = 5,
                    ),
                ),
            ).single()
            database.setLogDao().insertSet(
                SetLogEntity(
                    sessionExerciseId = sessionExerciseId,
                    setIndex = 1,
                    weightKg = 60.0,
                    reps = 5,
                    completedAtEpochMs = 3_500L,
                ),
            )
            val sets = database.setLogDao().observeSetsOfSession(sessionId).first()
            assertEquals(listOf(60.0), sets.map { it.weightKg })
            assertEquals(listOf(5), sets.map { it.reps })

            // Migracja 5→6: przerwy między seriami. Przerwa startuje otwarta,
            // domyka się raz (drugie domknięcie nic nie zmienia), a wiersz serii
            // dostaje faktyczny czas przerwy.
            val setLogId = sets.single().id
            val restId = database.restDao().insert(
                RestIntervalEntity(
                    exerciseId = exerciseId,
                    sessionId = sessionId,
                    setLogId = setLogId,
                    startedAtEpochMs = 4_000L,
                    plannedSeconds = 90,
                ),
            )
            assertEquals(restId, requireNotNull(database.restDao().getOpen()).id)
            assertEquals(
                1,
                database.restDao().close(
                    intervalId = restId,
                    endedAtEpochMs = 4_094L,
                    actualSeconds = 94,
                    plannedSeconds = 90,
                    reason = RestIntervalEntity.REASON_COMPLETED,
                ),
            )
            assertEquals(
                0,
                database.restDao().close(
                    intervalId = restId,
                    endedAtEpochMs = 4_100L,
                    actualSeconds = 100,
                    plannedSeconds = 90,
                    reason = RestIntervalEntity.REASON_SKIPPED,
                ),
            )
            val rest = requireNotNull(database.restDao().get(restId))
            assertEquals(94, rest.actualSeconds)
            assertEquals(RestIntervalEntity.REASON_COMPLETED, rest.endedReason)
            assertNull(database.restDao().getOpen())

            database.setLogDao().setRest(setLogId, actualSeconds = 94, targetSeconds = 90)
            val setWithRest = requireNotNull(database.setLogDao().getSet(setLogId))
            assertEquals(94, setWithRest.restAfterS)
            assertEquals(90, setWithRest.restTargetS)

            // Kaskada: usunięcie planu zabiera dni, ćwiczenia dni i reguły,
            // a wpis w kalendarzu zostaje - tylko bez wskazania (SET NULL).
            database.planDao().deletePlan(planId)
            assertNull(database.planDao().getDay(dayId))
            assertTrue(database.planDao().getDayExercises(dayId).isEmpty())
            assertNull(database.calendarDao().getRecurrence(recurrenceId))
            val orphan = database.calendarDao().getScheduledOn("2026-10-01").single()
            assertNull(orphan.planId)
            assertNull(orphan.planDayId)
            assertNull(orphan.recurrenceId)

            // Trening i jego serie przeżywają porządek w planach (sesja traci
            // tylko wskazania), więc historia zostaje czytelna.
            assertEquals(1, database.setLogDao().getSetsFrom(sessionExerciseId, 1).size)

            // Przerwa jest kopią wygodną: usunięcie serii nie zabiera historii
            // przerw (SET NULL), więc statystyki ćwiczenia zostają czytelne.
            database.setLogDao().deleteSet(setWithRest)
            assertNull(requireNotNull(database.restDao().get(restId)).setLogId)
        } finally {
            database.close()
        }
    }

    /**
     * Słownik powodów zakończenia przerwy pilnuje sam SQL (`CHECK` w migracji),
     * a nie tylko kod Kotlina - inaczej „powód” z literówki wszedłby do bazy
     * i wypadł ze statystyk po cichu.
     */
    @Test
    fun `baza odrzuca nieznany powod zakonczenia przerwy`() = runTest {
        createVersion1Database()

        val database = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .addMigrations(*DatabaseMigrations.ALL)
            .build()

        try {
            val exerciseId = database.exerciseDao().insert(
                ExerciseEntity(
                    name = "Plank",
                    createdAtEpochMs = 1_000L,
                    updatedAtEpochMs = 1_000L,
                ),
            )
            val restId = database.restDao().insert(
                RestIntervalEntity(
                    exerciseId = exerciseId,
                    startedAtEpochMs = 1_000L,
                    plannedSeconds = 60,
                ),
            )
            val raw = database.openHelper.writableDatabase

            val rejected = runCatching {
                raw.execSQL("UPDATE rest_intervals SET ended_reason = 'bogus' WHERE id = $restId")
            }.exceptionOrNull()
            assertNotNull("nieznany powód nie może wejść do bazy", rejected)

            // Powód ze słownika przechodzi.
            raw.execSQL("UPDATE rest_intervals SET ended_reason = 'skipped' WHERE id = $restId")
            assertEquals("skipped", requireNotNull(database.restDao().get(restId)).endedReason)
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
