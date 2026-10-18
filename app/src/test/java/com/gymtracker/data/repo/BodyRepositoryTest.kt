package com.gymtracker.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gymtracker.data.db.AppDatabase
import com.gymtracker.data.db.entities.MetricTypeEntity
import com.gymtracker.domain.model.MetricDirection
import com.gymtracker.domain.time.TimeProvider
import java.time.LocalDate
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
 * Karta postaci (M8): wskaźniki i pomiary.
 *
 * Sprawdzamy kontrakt, na którym opiera się UI: co znaczy pusta nazwa, co dzieje
 * się z drugim pomiarem tego samego dnia, co z przeniesieniem daty i co zostaje
 * po archiwizacji. Wbudowane wskaźniki zakłada **migracja**, więc tutaj (świeża
 * baza z encji) wstawiamy je ręcznie - ich obecność po migracji sprawdza
 * `SchemaMigrationTest`.
 */
@RunWith(RobolectricTestRunner::class)
class BodyRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var database: AppDatabase
    private lateinit var repository: BodyRepositoryImpl

    private var now = 1_000L

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = BodyRepositoryImpl(
            database = database,
            bodyDao = database.bodyDao(),
            timeProvider = TimeProvider { now },
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun insertType(
        name: String = "Waga",
        unit: String? = "kg",
        direction: MetricDirection = MetricDirection.NEUTRAL,
        builtinKey: String? = null,
        isArchived: Boolean = false,
        position: Int = 1,
    ): Long = database.bodyDao().insertType(
        MetricTypeEntity(
            name = name,
            unit = unit,
            direction = direction.wire,
            builtinKey = builtinKey,
            isArchived = isArchived,
            position = position,
            createdAtEpochMs = now,
        ),
    )

    @Test
    fun `nowy wskaznik dostaje pozycje za istniejacymi i przycieta nazwe`() = runTest {
        insertType(name = "Waga", position = 1)

        val id = repository.addType("  Obwód bicepsa ", " cm ", MetricDirection.UP_GOOD, 40.0)

        assertNotNull(id)
        val types = repository.observeTypes().first()
        assertEquals(listOf("Waga", "Obwód bicepsa"), types.map { type -> type.name })
        assertEquals(2, types.last().position)
        assertEquals("cm", types.last().unit)
        assertEquals(MetricDirection.UP_GOOD, types.last().direction)
        assertEquals(40.0, types.last().target)
    }

    @Test
    fun `wskaźnik bez nazwy nie powstaje`() = runTest {
        assertNull(repository.addType("   ", "kg", MetricDirection.NEUTRAL, null))

        assertTrue(repository.observeTypes().first().isEmpty())
    }

    @Test
    fun `pusta jednostka znaczy brak jednostki`() = runTest {
        repository.addType("Przysiady", "   ", MetricDirection.NEUTRAL, null)

        assertNull(repository.observeTypes().first().single().unit)
    }

    @Test
    fun `archiwizacja chowa wskaznik z listy aktywnej, ale nie kasuje go`() = runTest {
        val id = insertType(name = "Waga")
        val archived = insertType(name = "Szyja", isArchived = true, position = 2)

        assertEquals(listOf("Waga"), repository.observeTypes().first().map { type -> type.name })
        assertEquals(
            listOf("Waga", "Szyja"),
            repository.observeTypes(includeArchived = true).first().map { type -> type.name },
        )

        repository.setArchived(id, archived = true)
        assertTrue(repository.observeTypes().first().isEmpty())
        assertTrue(repository.observeTypes(includeArchived = true).first().all { type -> type.isArchived })

        repository.setArchived(archived, archived = false)
        assertEquals(
            listOf("Szyja"),
            repository.observeTypes().first().map { type -> type.name },
        )
    }

    @Test
    fun `drugi pomiar tego samego dnia poprawia pierwszy`() = runTest {
        val typeId = insertType()
        val day = LocalDate.of(2026, 9, 2)

        repository.saveEntry(typeId, null, day, 82.0, "rano")
        now = 2_000L
        repository.saveEntry(typeId, null, day, 81.5, null)

        val entries = repository.observeEntries(typeId).first()
        assertEquals(1, entries.size)
        assertEquals(81.5, entries.single().value, 0.0001)
        // Notatka przepada razem z poprzednią wartością: to jedna poprawka dnia,
        // a nie dopisek do starszego pomiaru.
        assertNull(entries.single().note)
    }

    @Test
    fun `pomiar z innego dnia dopisuje sie obok`() = runTest {
        val typeId = insertType()

        repository.saveEntry(typeId, null, LocalDate.of(2026, 9, 1), 82.0, null)
        repository.saveEntry(typeId, null, LocalDate.of(2026, 9, 2), 81.5, "po treningu")

        val entries = repository.observeEntries(typeId).first()
        // Od najnowszych.
        assertEquals(listOf(LocalDate.parse("2026-09-02"), LocalDate.parse("2026-09-01")), entries.map { it.date })
        assertEquals("po treningu", entries.first().note)
    }

    @Test
    fun `poprawka przenosi pomiar na nowy dzien`() = runTest {
        val typeId = insertType()
        repository.saveEntry(typeId, null, LocalDate.of(2026, 9, 1), 82.0, null)
        val entry = repository.observeEntries(typeId).first().single()

        repository.saveEntry(typeId, entry.id, LocalDate.of(2026, 9, 3), 82.0, null)

        val entries = repository.observeEntries(typeId).first()
        assertEquals(1, entries.size)
        assertEquals(LocalDate.parse("2026-09-03"), entries.single().date)
        assertEquals(entry.id, entries.single().id)
    }

    @Test
    fun `przeniesienie na zajety dzien zostawia tylko przenoszony pomiar`() = runTest {
        val typeId = insertType()
        repository.saveEntry(typeId, null, LocalDate.of(2026, 9, 1), 82.0, "stary")
        val target = LocalDate.of(2026, 9, 3)
        repository.saveEntry(typeId, null, target, 80.0, "nowszy")
        val moved = repository.observeEntries(typeId).first().last()

        repository.saveEntry(typeId, moved.id, target, 82.0, "stary")

        val entries = repository.observeEntries(typeId).first()
        assertEquals(1, entries.size)
        assertEquals(moved.id, entries.single().id)
        assertEquals(82.0, entries.single().value, 0.0001)
        assertEquals("stary", entries.single().note)
    }

    @Test
    fun `pusta nazwa przy poprawce zachowuje nazwe i klucz wbudowanego wskaznika`() = runTest {
        val id = insertType(name = "Weight", builtinKey = "weight")

        repository.updateType(id, name = "  ", unit = "kg", direction = MetricDirection.DOWN_GOOD, target = 78.0)

        val type = repository.observeTypes().first().single()
        assertEquals("Weight", type.name)
        assertNotNull(type.builtin)
        assertEquals(MetricDirection.DOWN_GOOD, type.direction)
        assertEquals(78.0, type.target)
    }

    @Test
    fun `wlasna nazwa odbiera wskaznikowi klucz wbudowanego`() = runTest {
        val id = insertType(name = "Weight", builtinKey = "weight")

        repository.updateType(id, name = "Waga ciała", unit = "kg", direction = MetricDirection.NEUTRAL, target = null)

        val type = repository.observeTypes().first().single()
        assertEquals("Waga ciała", type.name)
        assertNull(type.builtin)
    }

    @Test
    fun `usuniecie pomiaru nie rusza innych`() = runTest {
        val typeId = insertType()
        repository.saveEntry(typeId, null, LocalDate.of(2026, 9, 1), 82.0, null)
        repository.saveEntry(typeId, null, LocalDate.of(2026, 9, 2), 81.5, null)
        val older = repository.observeEntries(typeId).first().last()

        repository.deleteEntry(older.id)

        val entries = repository.observeEntries(typeId).first()
        assertEquals(1, entries.size)
        assertEquals(LocalDate.parse("2026-09-02"), entries.single().date)
    }

    @Test
    fun `pomiary dwoch wskaznikow sie nie mieszaja`() = runTest {
        val weight = insertType(name = "Waga", unit = "kg", position = 1)
        val biceps = insertType(name = "Obwód bicepsa", unit = "cm", position = 2)

        repository.saveEntry(weight, null, LocalDate.of(2026, 9, 2), 81.5, null)
        repository.saveEntry(biceps, null, LocalDate.of(2026, 9, 2), 39.0, null)

        assertEquals(listOf(81.5), repository.observeEntries(weight).first().map { it.value })
        assertEquals(listOf(39.0), repository.observeEntries(biceps).first().map { it.value })
    }

    @Test
    fun `pomiar bez wskaznika nie istnieje`() = runTest {
        val typeId = insertType()
        repository.saveEntry(typeId, null, LocalDate.of(2026, 9, 2), 81.5, null)
        assertEquals(1, database.bodyDao().allEntries().size)

        // Kasujemy wskaźnik wprost w bazie (użytkownik tego nie robi - archiwizuje),
        // żeby potwierdzić kaskadę: pomiar bez wskaźnika nie znaczy nic.
        database.openHelper.writableDatabase.execSQL("DELETE FROM metric_types")

        assertTrue(database.bodyDao().allEntries().isEmpty())
    }
}
