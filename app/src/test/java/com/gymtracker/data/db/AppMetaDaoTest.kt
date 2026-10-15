package com.gymtracker.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gymtracker.data.db.daos.AppMetaDao
import com.gymtracker.data.db.entities.AppMetaEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Test bazy na JVM (Robolectric), bez emulatora. Sprawdza, że schemat Room
 * się otwiera i że DAO działa - to nasza siatka bezpieczeństwa przy zmianach
 * schematu w kolejnych krokach.
 */
@RunWith(RobolectricTestRunner::class)
class AppMetaDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: AppMetaDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        dao = database.appMetaDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `zapis i odczyt wartosci`() = runTest {
        dao.upsert(AppMetaEntity(key = "catalog_version", value = "1", updatedAtEpochMs = 1_000L))

        assertEquals("1", dao.get("catalog_version"))
        assertEquals(1, dao.count())
    }

    @Test
    fun `ponowny zapis tego samego klucza nadpisuje wartosc`() = runTest {
        dao.upsert(AppMetaEntity(key = "catalog_version", value = "1", updatedAtEpochMs = 1_000L))
        dao.upsert(AppMetaEntity(key = "catalog_version", value = "2", updatedAtEpochMs = 2_000L))

        assertEquals("2", dao.get("catalog_version"))
        assertEquals(1, dao.count())
    }

    @Test
    fun `brakujacy klucz zwraca null`() = runTest {
        assertNull(dao.get("nie-ma-takiego-klucza"))
    }

    @Test
    fun `observeAll zwraca wpisy posortowane po kluczu`() = runTest {
        dao.upsert(AppMetaEntity(key = "b", value = "2", updatedAtEpochMs = 1L))
        dao.upsert(AppMetaEntity(key = "a", value = "1", updatedAtEpochMs = 1L))

        assertEquals(listOf("a", "b"), dao.observeAll().first().map { it.key })
    }

    @Test
    fun `delete usuwa wpis`() = runTest {
        dao.upsert(AppMetaEntity(key = "catalog_version", value = "1", updatedAtEpochMs = 1L))

        dao.delete("catalog_version")

        assertNull(dao.get("catalog_version"))
    }
}
