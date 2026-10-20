package com.gymtracker.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gymtracker.data.db.entities.BodyEntryEntity
import com.gymtracker.data.db.entities.ExerciseEntity
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
import com.gymtracker.data.search.ExerciseSearchQuery
import com.gymtracker.domain.model.ExerciseTracking
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
 * aplikacja z M0), otwiera ją kodem, który zna wersję 9, i sprawdza, że:
 *
 *  * dane użytkownika przetrwały,
 *  * Room zaakceptował schemat po migracji (sam porównuje go z oczekiwanym),
 *  * katalog ćwiczeń działa, łącznie z indeksem wyszukiwania założonym w migracji,
 *  * kalendarz z migracji 3→4 przyjmuje reguły i wpisy,
 *  * logowanie serii i pauza z migracji 4→5 działają na starych sesjach,
 *  * przerwy z migracji 5→6 da się zapisać i domknąć, a baza pilnuje słownika
 *    powodów zakończenia,
 *  * kroki wykonania i załączniki z migracji 6→7 da się zapisać, a załącznik
 *    znika razem z ćwiczeniem (kaskada),
 *  * karta postaci z migracji 7→8 działa: migracja zakłada dziesięć wbudowanych
 *    wskaźników, pomiar da się zapisać, drugi pomiar tego samego dnia jest
 *    odrzucany kluczem `UNIQUE`, a usunięcie wskaźnika zabiera jego pomiary,
 *  * dziennik importów z migracji 8→9 przyjmuje wpisy (także powtórzone klucze
 *    indeksu - indeks jest zwykły, nie unikalny).
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
    fun `migracja 1 do 11 zachowuje dane i dodaje katalog, plany, kalendarz, serie, przerwy, media, wskazniki, dziennik importow, dzien treningu i typ zapisu`() = runTest {
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

            // Migracja 6→7: kroki wykonania jako JSON w kolumnie ćwiczenia.
            // Kolumna jest zwykłym `TEXT`-em, więc zapis i odczyt muszą przejść
            // na wierszu, który powstał jeszcze w wersji 1.
            val beforeInstructions = requireNotNull(database.exerciseDao().getById(exerciseId))
            database.exerciseDao().update(
                beforeInstructions.copy(instructions = "[\"Ustaw sztangę\",\"Wyciśnij\"]"),
            )
            assertEquals(
                "[\"Ustaw sztangę\",\"Wyciśnij\"]",
                requireNotNull(database.exerciseDao().getById(exerciseId)).instructions,
            )

            // Załącznik z galerii to wskazanie na plik (`remote_uri`), a nasza
            // jest tylko miniatura - i to ona trafia na listę do sprzątania.
            val mediaId = database.mediaDao().insert(
                ExerciseMediaEntity(
                    exerciseId = exerciseId,
                    kind = "image",
                    remoteUri = "content://media/picker/1",
                    thumbnailPath = "thumbs/1.jpg",
                    position = 0,
                    createdAtEpochMs = 5_000L,
                ),
            )
            val media = database.mediaDao().observeMedia(exerciseId).first().single()
            assertEquals(mediaId, media.id)
            assertEquals("image", media.kind)
            assertEquals("content://media/picker/1", media.remoteUri)
            assertEquals(listOf("thumbs/1.jpg"), database.mediaDao().thumbnailPaths())
            assertEquals(0, database.mediaDao().maxPosition(exerciseId))
            assertEquals(-1, database.mediaDao().maxPosition(exerciseId + 1))

            // Migracja 7→8: karta postaci. Wbudowane wskaźniki zakłada migracja,
            // a klucz (`builtin_key`) pozwala pokazać ich nazwę w języku interfejsu.
            val builtins = database.bodyDao().observeActiveTypes().first()
            assertEquals(10, builtins.size)
            val weight = builtins.single { type -> type.builtinKey == "weight" }
            assertEquals("Weight", weight.name)
            assertEquals("kg", weight.unit)
            assertEquals(1, weight.position)
            assertEquals("neutral", weight.direction)

            val metricId = database.bodyDao().insertType(
                MetricTypeEntity(
                    name = "Obwód bicepsa",
                    unit = "cm",
                    direction = "up_good",
                    position = 11,
                    createdAtEpochMs = 6_000L,
                ),
            )
            val entryId = database.bodyDao().insertEntry(
                BodyEntryEntity(
                    metricTypeId = metricId,
                    date = "2026-09-02",
                    measuredAtEpochMs = 6_000L,
                    value = 39.5,
                    note = "po treningu",
                ),
            )
            val entry = database.bodyDao().observeEntries(metricId).first().single()
            assertEquals(entryId, entry.id)
            assertEquals(39.5, entry.value, 0.0001)
            assertEquals("po treningu", entry.note)

            // Jeden pomiar na dzień pilnuje klucz `UNIQUE`, a nie tylko kod Kotlina.
            val duplicate = runCatching {
                database.bodyDao().insertEntry(
                    BodyEntryEntity(
                        metricTypeId = metricId,
                        date = "2026-09-02",
                        measuredAtEpochMs = 7_000L,
                        value = 40.0,
                    ),
                )
            }.exceptionOrNull()
            assertNotNull("drugi pomiar tego samego dnia nie może wejść do bazy", duplicate)

            // Kaskada: pomiar bez wskaźnika nie znaczy nic.
            database.openHelper.writableDatabase.execSQL("DELETE FROM metric_types")
            assertTrue(database.bodyDao().allEntries().isEmpty())

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

            // Migracja 8→9: dziennik importów. Wpis wchodzi, indeks jest zwykły
            // (ten sam plik może być wczytany kilka razy), a metadane z M0 są
            // nietknięte - dziennik dokłada się obok, nie zamiast.
            database.seedImportDao().insert(
                SeedImportEntity(
                    seedId = "moje-cwiczenia.json",
                    version = "2026-10-01",
                    checksum = "abc123",
                    importedAtEpochMs = 8_000L,
                    statsJson = "{\"status\":\"done\",\"imported\":5,\"skipped\":0,\"total\":5}",
                ),
            )
            database.seedImportDao().insert(
                SeedImportEntity(
                    seedId = "moje-cwiczenia.json",
                    version = "2026-10-01",
                    checksum = "abc123",
                    importedAtEpochMs = 9_000L,
                    statsJson = "{\"status\":\"done\",\"imported\":0,\"skipped\":5,\"total\":5}",
                ),
            )
            assertEquals(2, database.seedImportDao().observeRecent(10).first().size)
            assertEquals(
                9_000L,
                requireNotNull(database.seedImportDao().findLastWithChecksum("abc123")).importedAtEpochMs,
            )
            assertEquals("1", database.appMetaDao().get("catalog_version"))

            // Migracja 9→10: dzień treningu. Sesja z M1-M9 nie ma go wcale
            // (dniem jest dzień startu), a nowa niesie dzień z kalendarza -
            // kolumna jest zwykłym `TEXT`-em, bez przeliczania dat w SQL.
            assertNull(requireNotNull(database.sessionDao().getSession(sessionId)).sessionDate)
            val datedSessionId = database.sessionDao().insertSession(
                SessionEntity(
                    name = "Trening z wpisu",
                    sessionDate = "2026-10-27",
                    startedAtEpochMs = 10_000L,
                    status = SessionEntity.STATUS_ACTIVE,
                ),
            )
            assertEquals(
                "2026-10-27",
                requireNotNull(database.sessionDao().getSession(datedSessionId)).sessionDate,
            )

            // Migracja 10→11: typ zapisu serii. Stare wiersze dostają bezpieczną
            // wartość domyślną, więc historia nie zmienia znaczenia; nowe pole
            // da się zapisać i odczytać zarówno w katalogu, jak i w treningu.
            assertEquals(
                "weight_reps",
                requireNotNull(database.exerciseDao().getById(exerciseId)).tracking,
            )
            database.exerciseDao().update(
                requireNotNull(database.exerciseDao().getById(exerciseId))
                    .copy(tracking = ExerciseTracking.TIME.wire),
            )
            assertEquals(
                "time",
                requireNotNull(database.exerciseDao().getById(exerciseId)).tracking,
            )

            assertEquals(
                "weight_reps",
                requireNotNull(database.sessionDao().getSessionExercises(sessionId).single()).tracking,
            )
            database.openHelper.writableDatabase.execSQL(
                "UPDATE session_exercises SET tracking = 'time' WHERE id = $sessionExerciseId",
            )
            assertEquals(
                "time",
                requireNotNull(database.sessionDao().getSessionExercises(sessionId).single()).tracking,
            )
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

    /**
     * Załącznik należy do ćwiczenia: skasowanie ćwiczenia ma zostawić po nim
     * pustkę, a nie osierocone wiersze (pliki sprząta aplikacja przy starcie).
     */
    @Test
    fun `usuniecie cwiczenia zabiera jego zalaczniki`() = runTest {
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
            database.mediaDao().insert(
                ExerciseMediaEntity(
                    exerciseId = exerciseId,
                    kind = "video",
                    remoteUri = "content://media/picker/2",
                    position = 0,
                    createdAtEpochMs = 1_000L,
                ),
            )
            assertEquals(1, database.mediaDao().observeMedia(exerciseId).first().size)

            database.exerciseDao().deleteById(exerciseId)

            assertTrue(database.mediaDao().observeMedia(exerciseId).first().isEmpty())
        } finally {
            database.close()
        }
    }

    /**
     * Słownik rodzajów załącznika pilnuje SQL z migracji (`CHECK`), a nie tylko
     * kod Kotlina - inaczej rodzaj z literówki wszedłby do bazy i wypadł z siatki
     * po cichu (mapowanie nieznanych rodzajów pomija wiersz).
     */
    @Test
    fun `baza odrzuca nieznany rodzaj zalacznika`() = runTest {
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
            val mediaId = database.mediaDao().insert(
                ExerciseMediaEntity(
                    exerciseId = exerciseId,
                    kind = "image",
                    remoteUri = "content://media/picker/3",
                    position = 0,
                    createdAtEpochMs = 1_000L,
                ),
            )
            val raw = database.openHelper.writableDatabase

            val rejected = runCatching {
                raw.execSQL("UPDATE exercise_media SET kind = 'hologram' WHERE id = $mediaId")
            }.exceptionOrNull()
            assertNotNull("nieznany rodzaj nie może wejść do bazy", rejected)

            // Rodzaj ze słownika przechodzi.
            raw.execSQL("UPDATE exercise_media SET kind = 'video' WHERE id = $mediaId")
            assertEquals("video", requireNotNull(database.mediaDao().findById(mediaId)).kind)
        } finally {
            database.close()
        }
    }

    /**
     * Słownik kierunków zmiany pilnuje SQL z migracji (`CHECK`), a nie tylko kod
     * Kotlina - inaczej kierunek z literówki wszedłby do bazy i zmiana nigdy nie
     * dostałaby koloru (mapowanie nieznanych kierunków daje `neutral`).
     */
    @Test
    fun `baza odrzuca nieznany kierunek zmian wskaznika`() = runTest {
        createVersion1Database()

        val database = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .addMigrations(*DatabaseMigrations.ALL)
            .build()

        try {
            val raw = database.openHelper.writableDatabase
            val typeId = database.bodyDao().observeActiveTypes().first().single { type ->
                type.builtinKey == "weight"
            }.id

            val rejected = runCatching {
                raw.execSQL("UPDATE metric_types SET direction = 'w_gore_dobrze' WHERE id = $typeId")
            }.exceptionOrNull()
            assertNotNull("nieznany kierunek nie może wejść do bazy", rejected)

            // Kierunek ze słownika przechodzi i zaraz jest widoczny w modelu.
            raw.execSQL("UPDATE metric_types SET direction = 'down_good' WHERE id = $typeId")
            val weight = database.bodyDao().observeActiveTypes().first().single { type -> type.id == typeId }
            assertEquals("down_good", weight.direction)
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
