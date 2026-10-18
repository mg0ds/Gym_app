package com.gymtracker.data.seed

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gymtracker.data.db.AppDatabase
import com.gymtracker.domain.time.TimeProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Import katalogu z pliku użytkownika (M9).
 *
 * Ten sam format, co dane startowe, ale **inny kontrakt**: pliku nie wydajemy
 * razem z aplikacją, więc może w nim brakować wszystkiego. Dlatego każdy
 * przypadek kończy się albo wpisem w bazie, albo **konkretnym powodem**
 * w dzienniku - i oba warianty sprawdzamy, bo bez dziennika odrzucony plik
 * byłby dla użytkownika nierozróżnialny od „aplikacja nie działa”.
 */
@RunWith(RobolectricTestRunner::class)
class CatalogImportTest {

    private lateinit var database: AppDatabase
    private lateinit var journal: SeedImportJournal
    private lateinit var importer: SeedImporter

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        journal = SeedImportJournal(
            dao = database.seedImportDao(),
            timeProvider = TimeProvider { NOW },
        )
        importer = SeedImporter(
            seedSource = AssetSeedSource(ApplicationProvider.getApplicationContext()),
            exerciseDao = database.exerciseDao(),
            planDao = database.planDao(),
            appMetaDao = database.appMetaDao(),
            journal = journal,
            timeProvider = TimeProvider { NOW },
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `wczytuje katalog z pliku i zapisuje wpis w dzienniku`() = runTest {
        val result = importer.importCatalogFile("moje-cwiczenia.json", catalog("squat", "bench-press"))

        val done = requireNotNull(result as? CatalogImportResult.Done)
        assertEquals(2, done.imported)
        assertEquals(0, done.skipped)
        assertEquals(2, database.exerciseDao().count())

        val entry = journal.observeRecent().first().single()
        assertEquals("moje-cwiczenia.json", entry.seedId)
        assertEquals("2026-10-01", entry.version)
        assertEquals(SeedImportStats.STATUS_DONE, entry.stats?.status)
        assertEquals(2, entry.stats?.imported)

        // Odcisk treści pozwala później rozpoznać „ten sam plik jeszcze raz”.
        val stored = requireNotNull(
            database.seedImportDao().findLastWithChecksum(sha256Hex(catalog("squat", "bench-press"))),
        )
        assertEquals(64, requireNotNull(stored.checksum).length)
    }

    @Test
    fun `ten sam plik wczytany drugi raz nic nie dodaje`() = runTest {
        val text = catalog("squat", "bench-press")

        val first = requireNotNull(importer.importCatalogFile("katalog.json", text) as? CatalogImportResult.Done)
        val second = requireNotNull(importer.importCatalogFile("katalog.json", text) as? CatalogImportResult.Done)

        assertEquals(2, first.imported)
        assertEquals(0, second.imported)
        assertEquals(2, second.skipped)
        assertEquals(2, database.exerciseDao().count())

        // Dziennik pamięta obie próby i pozwala je rozpoznać po treści pliku.
        val entries = journal.observeRecent().first()
        assertEquals(2, entries.size)
        assertEquals(listOf("katalog.json", "katalog.json"), entries.map { entry -> entry.seedId })
    }

    @Test
    fun `inna tresc pliku to inny odcisk`() = runTest {
        val first = sha256Hex(catalog("squat", "bench-press"))
        val second = sha256Hex(catalog("squat"))

        assertNotEquals(first, second)
        assertEquals(first, sha256Hex(catalog("squat", "bench-press")))
        assertEquals(64, first.length)
    }

    @Test
    fun `brak wersji schematu to odrzucenie z powodem`() = runTest {
        val result = importer.importCatalogFile("bez-wersji.json", """{"exercises":[{"id":"squat","name":"Squat"}]}""")

        assertEquals(
            SeedImportErrors.MISSING_SCHEMA_VERSION,
            requireNotNull(result as? CatalogImportResult.Rejected).errorCode,
        )
        assertEquals(0, database.exerciseDao().count())
        assertTrue(journal.observeRecent().first().single().isRejected)
    }

    @Test
    fun `plik z nowszej wersji formatu jest odrzucany`() = runTest {
        val text = """{"schema_version":2,"generated_at":"2026-10-01","exercises":[{"id":"squat","name":"Squat"}]}"""

        assertEquals(
            SeedImportErrors.TOO_NEW_SCHEMA,
            requireNotNull(importer.importCatalogFile("nowy.json", text) as? CatalogImportResult.Rejected).errorCode,
        )
        assertEquals(0, database.exerciseDao().count())
    }

    @Test
    fun `pusty katalog jest odrzucany`() = runTest {
        val text = """{"schema_version":1,"generated_at":"2026-10-01","exercises":[]}"""

        assertEquals(
            SeedImportErrors.EMPTY_CATALOG,
            requireNotNull(importer.importCatalogFile("pusty.json", text) as? CatalogImportResult.Rejected).errorCode,
        )
    }

    @Test
    fun `niepoprawny json jest odrzucany`() = runTest {
        assertEquals(
            SeedImportErrors.BAD_JSON,
            requireNotNull(importer.importCatalogFile("smieci.json", "nie json") as? CatalogImportResult.Rejected).errorCode,
        )
    }

    @Test
    fun `pozycja bez wymaganych pol jest odrzucana`() = runTest {
        val text = """{"schema_version":1,"generated_at":"2026-10-01","exercises":[{"id":"squat"}]}"""

        assertEquals(
            SeedImportErrors.BAD_JSON,
            requireNotNull(importer.importCatalogFile("braki.json", text) as? CatalogImportResult.Rejected).errorCode,
        )
        assertEquals(0, database.exerciseDao().count())
    }

    @Test
    fun `dodatkowe pola w pliku nie przeszkadzaja`() = runTest {
        // Plik może pochodzić z innego narzędzia - nie znamy wszystkich pól
        // i nie mamy prawa go z tego powodu odrzucać.
        val text = """
            {
              "schema_version": 1,
              "generated_at": "2026-10-01",
              "autor": "ktos",
              "exercises": [
                {"id": "squat", "name": "Przysiad", "moje_pole": 42}
              ]
            }
        """.trimIndent()

        val done = requireNotNull(importer.importCatalogFile("dodatkowe.json", text) as? CatalogImportResult.Done)
        assertEquals(1, done.imported)
        assertEquals("Przysiad", database.exerciseDao().getByExternalId("squat")?.name)
    }

    /** Katalog w formacie danych startowych, z podanymi identyfikatorami. */    private fun catalog(vararg ids: String): String {
        val exercises = ids.joinToString(",") { id ->
            """{"id":"$id","name":"Ćwiczenie $id","muscle_group":"Nogi"}"""
        }
        return """{"schema_version":1,"generated_at":"2026-10-01","exercises":[$exercises]}"""
    }

    private companion object {
        const val NOW = 1_760_000_000_000L
    }
}
