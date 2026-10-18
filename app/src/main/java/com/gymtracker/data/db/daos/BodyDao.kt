package com.gymtracker.data.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.gymtracker.data.db.entities.BodyEntryEntity
import com.gymtracker.data.db.entities.MetricTypeEntity
import kotlinx.coroutines.flow.Flow

/**
 * Karta postaci (M8): wskaźniki i ich pomiary.
 *
 * Podział pracy jest ten sam co w statystykach: SQL oddaje wiersze, a reguły
 * (co widać w zakresie, jak liczy się zmiana i„seria”) są w
 * `BodyProgressCalculator`. Dlatego nie ma tu zapytań agregujących - pojedynczy
 * wskaźnik ma w historii dziesiątki, nie tysiące pomiarów.
 *
 * Limitu wierszy też nie ma, w przeciwieństwie do `set_logs`: tam dziesiątki
 * tysięcy serii kazały ciąć zapytanie, a tu cała historia wagi z pięciu lat to
 * ~250 wierszy i wykres po zakresie potrzebuje ich wszystkich.
 */
@Dao
interface BodyDao {

    /** Aktywne wskaźniki - kolejność nadaje `position` (wbudowane 1..10). */
    @Query("SELECT * FROM metric_types WHERE is_archived = 0 ORDER BY position, id")
    fun observeActiveTypes(): Flow<List<MetricTypeEntity>>

    /** Wszystkie wskaźniki, także zarchiwizowane - do ekranu „Wskaźniki”. */
    @Query("SELECT * FROM metric_types ORDER BY position, id")
    fun observeAllTypes(): Flow<List<MetricTypeEntity>>

    @Query("SELECT * FROM metric_types WHERE id = :typeId")
    fun observeType(typeId: Long): Flow<MetricTypeEntity?>

    @Query("SELECT * FROM metric_types WHERE id = :typeId")
    suspend fun findType(typeId: Long): MetricTypeEntity?

    /** Najwyższa zajęta pozycja; `-1`, gdy nie ma jeszcze żadnego wskaźnika. */
    @Query("SELECT COALESCE(MAX(position), -1) FROM metric_types")
    suspend fun maxTypePosition(): Int

    @Insert
    suspend fun insertTypes(types: List<MetricTypeEntity>): List<Long>

    @Insert
    suspend fun insertType(type: MetricTypeEntity): Long

    /**
     * Zmiana ustawień wskaźnika.
     *
     * [builtinKey] przychodzi z repozytorium, a nie z zapytania: to ono wie, czy
     * nazwa faktycznie się zmieniła (wtedy klucz wbudowanego wskaźnika
     * przepada, bo od tej chwili pokazujemy nazwę użytkownika).
     */
    @Query(
        "UPDATE metric_types SET name = :name, unit = :unit, direction = :direction, " +
            "target_num = :target, builtin_key = :builtinKey WHERE id = :typeId",
    )
    suspend fun updateType(
        typeId: Long,
        name: String,
        unit: String?,
        direction: String,
        target: Double?,
        builtinKey: String?,
    )

    @Query("UPDATE metric_types SET is_archived = :archived WHERE id = :typeId")
    suspend fun setArchived(typeId: Long, archived: Boolean)

    /** Pomiary jednego wskaźnika od najnowszych. */
    @Query(
        "SELECT * FROM body_entries WHERE metric_type_id = :typeId " +
            "ORDER BY date DESC, measured_at DESC, id DESC",
    )
    fun observeEntries(typeId: Long): Flow<List<BodyEntryEntity>>

    /** Pomiar z danego dnia; `null`, gdy tego dnia jeszcze nic nie zapisano. */
    @Query(
        "SELECT * FROM body_entries WHERE metric_type_id = :typeId AND date = :date " +
            "ORDER BY measured_at DESC, id DESC LIMIT 1",
    )
    suspend fun findEntry(typeId: Long, date: String): BodyEntryEntity?

    @Insert
    suspend fun insertEntry(entry: BodyEntryEntity): Long

    @Query(
        "UPDATE body_entries SET date = :date, measured_at = :measuredAtEpochMs, " +
            "value_num = :value, note = :note WHERE id = :entryId",
    )
    suspend fun updateEntry(
        entryId: Long,
        date: String,
        measuredAtEpochMs: Long,
        value: Double,
        note: String?,
    )

    @Query("DELETE FROM body_entries WHERE id = :entryId")
    suspend fun deleteEntry(entryId: Long)

    /**
     * Kasuje pomiar z danego dnia - ale nie ten, który właśnie zapisujemy.
     *
     * Używane, gdy użytkownik przenosi pomiar na dzień, który już ma pomiar:
     * wtedy „jeden pomiar na dzień” znaczy „zostaje ten, który właśnie
     * wpisałem”, a nie błąd klucza `UNIQUE`.
     */
    @Query(
        "DELETE FROM body_entries WHERE metric_type_id = :typeId AND date = :date " +
            "AND id != :keepEntryId",
    )
    suspend fun deleteEntryOnDate(typeId: Long, date: String, keepEntryId: Long)

    /** Wszystkie wiersze pomiarów - testy migracji i porządki. */
    @Query("SELECT * FROM body_entries")
    suspend fun allEntries(): List<BodyEntryEntity>
}
