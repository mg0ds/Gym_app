package com.gymtracker.data.seed

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gymtracker.data.db.AppDatabase
import com.gymtracker.data.db.entities.SeedImportEntity
import com.gymtracker.domain.time.TimeProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Dziennik importów: kolejność, limity i odporność na nieczytelny zapis.
 *
 * Ostatni punkt jest tu najważniejszy. Dziennik jest zapisem historycznym,
 * więc po zmianie formatu podsumowania stare wpisy zostaną z nieczytelnym
 * JSON-em - i muszą się pokazać bez szczegółów, a nie wywrócić ekranu
 * Ustawień. Historia, która potrafi wysadzić aplikację, byłaby gorsza niż jej
 * brak.
 */
@RunWith(RobolectricTestRunner::class)
class SeedImportJournalTest {

    private lateinit var database: AppDatabase
    private lateinit var journal: SeedImportJournal
    private var now = 1_000L

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        journal = SeedImportJournal(
            dao = database.seedImportDao(),
            timeProvider = TimeProvider { now },
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `najnowsze importy sa pierwsze a lista ma limit`() = runTest {
        repeat(7) { index ->
            now = 1_000L + index
            journal.recordDone(
                seedId = "plik-$index.json",
                version = "2026-10-0$index",
                checksum = null,
                stats = SeedImportStats.done(imported = index, skipped = 0, total = index),
            )
        }

        val entries = journal.observeRecent(limit = 3).first()

        assertEquals(listOf("plik-6.json", "plik-5.json", "plik-4.json"), entries.map { it.seedId })
        assertEquals(1_006L, entries.first().importedAtEpochMs)
    }

    @Test
    fun `odrzucony plik zostaje w dzienniku z powodem`() = runTest {
        journal.recordRejected("zle.json", checksum = "abc", errorCode = SeedImportErrors.EMPTY_CATALOG)

        val entry = journal.observeRecent().first().single()

        assertTrue(entry.isRejected)
        assertEquals(SeedImportErrors.EMPTY_CATALOG, entry.stats?.error)
        assertEquals(0, entry.stats?.imported)
    }

    @Test
    fun `nieczytelne podsumowanie pokazuje wpis bez szczegolow`() = runTest {
        database.seedImportDao().insert(
            SeedImportEntity(
                seedId = "stary.json",
                importedAtEpochMs = 500L,
                statsJson = "{to nie byl json",
            ),
        )

        val entry = journal.observeRecent().first().single()

        assertEquals("stary.json", entry.seedId)
        assertNull(entry.stats)
        assertFalse(entry.isRejected)
    }
}
