package com.gymtracker.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gymtracker.data.db.AppDatabase
import com.gymtracker.data.db.entities.ExerciseEntity
import com.gymtracker.data.db.entities.SessionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Kopia w obie strony na prawdziwej bazie: eksport wszystkich tabel, a potem
 * wczytanie w obu trybach.
 *
 * Sprawdzamy dwa kontrakty, których nie widać w pojedynczych zapytaniach:
 *
 *  * „zastąp” jest **wierny** - po wczytaniu kopia źródłowa i docelowa mają ten
 *    sam zestaw wierszy (nie tylko te same liczby: porównujemy całe tabele),
 *  * „dopisz” **nie gubi powiązań** - trening z kopii dowiązuje się do
 *    ćwiczenia, które w bazie już jest, zamiast zniknąć razem z duplikatem.
 *
 * Osobno pilnujemy dwóch zachowań, które łatwo zgubić przy zmianach: import
 * odmawia w trakcie treningu (stan w pamięci wskazywałby na nieistniejące
 * wiersze), a błąd w środku wycofuje **całość** - po nieudanym imporcie baza
 * musi wyglądać jak przed nim.
 */
@RunWith(RobolectricTestRunner::class)
class BackupRoundTripTest {

    private lateinit var sourceDb: AppDatabase
    private lateinit var targetDb: AppDatabase
    private lateinit var importer: BackupImporter

    @Before
    fun setUp() {
        sourceDb = newDatabase()
        targetDb = newDatabase()
        importer = BackupImporter(targetDb, targetDb.backupDao())
    }

    @After
    fun tearDown() {
        sourceDb.close()
        targetDb.close()
    }

    @Test
    fun `zastap przywraca wszystkie tabele wiernie`() = runTest {
        fillBackupData(sourceDb)
        val tables = readAllTables(sourceDb)

        // W bazie docelowej są już inne dane - mają zniknąć bez śladu.
        fillBackupData(targetDb, suffix = "-stare")

        val summary = importer.import(tables, BackupImportMode.REPLACE)

        assertEquals(BackupImportMode.REPLACE, summary.mode)
        assertEquals(tables.rowCount, summary.inserted)
        assertEquals(0, summary.reused)
        assertEquals(0, summary.skipped)
        assertEquals(tables, readAllTables(targetDb))
    }

    @Test
    fun `dopisz wykorzystuje istniejace cwiczenie i dowiazuje do niego historie`() = runTest {
        val source = fillBackupData(sourceDb)
        val tables = readAllTables(sourceDb)

        // W bazie docelowej jest już to samo ćwiczenie (np. z katalogu startowego),
        // ale pod innym identyfikatorem - dokładnie ten przypadek psuje naiwne
        // „dopisz”: wiersz ćwiczenia wypada i zabiera ze sobą całą historię.
        val existingSquatId = targetDb.backupDao().insertExercises(
            listOf(
                // Wiersz zapychający: identyfikatory w obu bazach muszą się różnić,
                // inaczej test nie odróżniłby dowiązania od przypadku.
                ExerciseEntity(
                    externalId = "cwiczenie-zapasowe",
                    name = "Ćwiczenie zapasowe",
                    createdAtEpochMs = 1_000L,
                    updatedAtEpochMs = 1_000L,
                ),
                ExerciseEntity(
                    externalId = "barbell-back-squat",
                    name = "Barbell Back Squat",
                    createdAtEpochMs = 1_000L,
                    updatedAtEpochMs = 1_000L,
                ),
            ),
        ).last()
        assertNotEquals(source.squatId, existingSquatId)

        val summary = importer.import(tables, BackupImportMode.APPEND)

        assertEquals(BackupImportMode.APPEND, summary.mode)
        assertEquals(1, summary.reused)
        assertEquals(0, summary.skipped)

        val target = readAllTables(targetDb)

        // Ćwiczenie nie ma duplikatu, a jego historia jest w bazie.
        assertEquals(3, target.exercises.size)
        assertTrue(target.exercises.any { exercise -> exercise.id == existingSquatId })
        assertEquals(1, target.sessions.size)

        val importedSet = target.setLogs.single()
        val importedEntry = target.sessionExercises.single { entry -> entry.id == importedSet.sessionExerciseId }
        assertEquals(existingSquatId, importedEntry.exerciseId)

        // Dzień planu wskazuje na istniejące ćwiczenie, a nie na kopię sprzed chwili.
        val importedPlanExercise = target.planDayExercises.single { entry -> entry.position == 0 }
        assertEquals(existingSquatId, importedPlanExercise.exerciseId)

        // Miniatura też trafia do istniejącego ćwiczenia.
        assertEquals(existingSquatId, target.exerciseMedia.single().exerciseId)

        val importedRest = target.restIntervals.single()
        assertEquals(existingSquatId, importedRest.exerciseId)
        assertEquals(importedSet.id, importedRest.setLogId)

        // Metadane nadpisują się po kluczu, dziennik importów dopisuje się obok.
        assertEquals(2, target.appMeta.size)
        assertEquals(1, target.seedImports.size)
    }

    @Test
    fun `dopisz scala cwiczenie uzytkownika po nazwie`() = runTest {
        val source = fillBackupData(sourceDb)
        val tables = readAllTables(sourceDb)

        // Własne ćwiczenie: bez `external_id` jedyne, po czym da się je
        // rozpoznać, to nazwa. Bez tego użytkownik zobaczyłby drugi raz to samo
        // ćwiczenie - a razem z nim drugą, rozjechaną historię.
        val customId = targetDb.backupDao().insertExercises(
            listOf(
                ExerciseEntity(name = "Zapychacz", createdAtEpochMs = 1_000L, updatedAtEpochMs = 1_000L),
                ExerciseEntity(name = "Barbell Back Squat", createdAtEpochMs = 1_000L, updatedAtEpochMs = 1_000L),
            ),
        ).last()
        assertNotEquals(source.squatId, customId)

        val summary = importer.import(tables, BackupImportMode.APPEND)

        assertEquals(1, summary.reused)
        val target = readAllTables(targetDb)
        assertEquals(3, target.exercises.size)

        // Historia dowiązuje się do ćwiczenia użytkownika, razem z seriami.
        val importedEntry = target.sessionExercises.single()
        assertEquals(customId, importedEntry.exerciseId)
        assertEquals(importedEntry.id, target.setLogs.single().sessionExerciseId)
    }

    @Test
    fun `dopisz nie tworzy drugiego dnia planu o tej samej pozycji`() = runTest {
        fillBackupData(sourceDb)
        val tables = readAllTables(sourceDb)

        // Ten sam plan i ten sam dzień: pozycja 0 jest już zajęta.
        val target = fillBackupData(targetDb)

        val summary = importer.import(tables, BackupImportMode.APPEND)

        // Ćwiczenia (2), plan (1) i wskaźnik karty postaci (1) to te same wiersze.
        assertEquals(4, summary.reused)
        // Dzień planu i jego dwa ćwiczenia wypadły (plan ma już taki dzień),
        // a pomiar na ten sam dzień przy scalonym wskaźniku nie wchodzi drugi raz.
        assertEquals(4, summary.skipped)

        val after = readAllTables(targetDb)
        assertEquals(2, after.exercises.size)
        assertEquals(1, after.planDays.size)
        assertEquals(after.planDays.single().id, target.planDayId)
        assertEquals(2, after.planDayExercises.size)

        // Historia treningu wchodzi mimo to, a wpis kalendarza zostaje bez dnia
        // (kolumna jest opcjonalna - inaczej ten sam dzień dwa razy łamałby
        // unikalność `(date, plan_day_id)`).
        assertEquals(2, after.sessions.size)
        assertEquals(1, after.scheduledSessions.count { entry -> entry.planDayId == null })

        // Wskaźnik karty postaci jest ten sam (klucz wbudowany), więc pomiar na
        // ten sam dzień nie wchodzi drugi raz - inaczej karta pokazywałaby dwie
        // „Wagi” i dwa odczyty z jednego dnia.
        assertEquals(1, after.metricTypes.size)
        assertEquals(1, after.bodyEntries.size)
    }

    @Test
    fun `import odmawia w trakcie treningu`() = runTest {
        fillBackupData(sourceDb)
        val tables = readAllTables(sourceDb)

        targetDb.backupDao().insertSessions(
            listOf(
                SessionEntity(
                    name = "Trening w toku",
                    startedAtEpochMs = 1_000L,
                    status = SessionEntity.STATUS_ACTIVE,
                ),
            ),
        )

        val error = runCatching { importer.import(tables, BackupImportMode.REPLACE) }.exceptionOrNull()

        assertEquals(BackupImportError.WORKOUT_IN_PROGRESS, (error as BackupImportException).error)
        // Nic się nie stało: trening w toku i brak wierszy z kopii.
        assertEquals(1, readAllTables(targetDb).sessions.size)
    }

    @Test
    fun `blad w srodku importu wycofuje calosc`() = runTest {
        targetDb.backupDao().insertExercises(
            listOf(
                ExerciseEntity(
                    externalId = "zostaje",
                    name = "Ćwiczenie, które ma zostać",
                    createdAtEpochMs = 1_000L,
                    updatedAtEpochMs = 1_000L,
                ),
            ),
        )

        // Dwa ćwiczenia o tym samym `external_id` - baza odrzuci drugie wiersze
        // w trakcie wstawiania, już po skasowaniu danych docelowych.
        val broken = BackupTables(
            exercises = listOf(
                ExerciseEntity(externalId = "duplikat", name = "Pierwsze", createdAtEpochMs = 1L, updatedAtEpochMs = 1L),
                ExerciseEntity(externalId = "duplikat", name = "Drugie", createdAtEpochMs = 1L, updatedAtEpochMs = 1L),
            ),
        )

        val error = runCatching { importer.import(broken, BackupImportMode.REPLACE) }.exceptionOrNull()

        assertTrue(error != null)
        val after = readAllTables(targetDb)
        assertEquals(1, after.exercises.size)
        assertEquals("zostaje", after.exercises.single().externalId)
        assertNull(after.sessions.firstOrNull())
    }

    private fun newDatabase(): AppDatabase = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        AppDatabase::class.java,
    ).build()
}
