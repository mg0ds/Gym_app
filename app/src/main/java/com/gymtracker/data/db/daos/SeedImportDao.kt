package com.gymtracker.data.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.gymtracker.data.db.entities.SeedImportEntity
import kotlinx.coroutines.flow.Flow

/**
 * Dziennik importów katalogów (M9). Zapisy są tylko dopisywane - nigdy nie
 * poprawiamy historii: pytanie brzmi „skąd przyszły te dane”, a nie „jaki jest
 * najnowszy stan”, więc nadpisywanie starszego wpisu zacierałoby odpowiedź.
 */
@Dao
interface SeedImportDao {

    @Insert
    suspend fun insert(entry: SeedImportEntity): Long

    /** Najnowsze wpisy - do listy w Ustawieniach. */
    @Query("SELECT * FROM seed_imports ORDER BY imported_at DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SeedImportEntity>>

    /** Ostatni wpis o tej samej treści pliku (ten sam `checksum`). */
    @Query("SELECT * FROM seed_imports WHERE checksum = :checksum ORDER BY imported_at DESC, id DESC LIMIT 1")
    suspend fun findLastWithChecksum(checksum: String): SeedImportEntity?
}
