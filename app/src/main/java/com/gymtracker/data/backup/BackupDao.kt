package com.gymtracker.data.backup

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.gymtracker.data.db.entities.AppMetaEntity
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

/**
 * Dostęp do bazy na potrzeby kopii zapasowej (M9).
 *
 * Wszystkie zapytania kopii są w jednym miejscu, żeby nie rozlewać po DAO
 * domenowych metod, których używa **tylko** eksport i import („wszystkie wiersze
 * tabeli”, „skasuj wszystko”). Dzięki temu widać cały kontrakt kopii w jednym
 * pliku: co wychodzi, co wchodzi i w jakiej kolejności - a kolejność jest tu
 * częścią poprawności, bo baza pilnuje kluczy obcych.
 *
 * Wstawianie idzie tym samym `@Insert`, co w codziennej pracy: wiersz
 * z ustawionym `id` zachowuje swój identyfikator (tryb „zastąp”), a wiersz
 * z `id = 0` dostaje nowy (tryb „dopisz”). Zwrócone identyfikatory są w tej
 * samej kolejności co wejście - na tym opiera się przepisanie powiązań.
 */
@Dao
interface BackupDao {

    // --- eksport: wszystko, co ma trafić do pliku ---

    @Query("SELECT * FROM exercises ORDER BY id")
    suspend fun allExercises(): List<ExerciseEntity>

    @Query("SELECT * FROM exercise_media ORDER BY id")
    suspend fun allExerciseMedia(): List<ExerciseMediaEntity>

    @Query("SELECT * FROM plans ORDER BY id")
    suspend fun allPlans(): List<PlanEntity>

    @Query("SELECT * FROM plan_days ORDER BY id")
    suspend fun allPlanDays(): List<PlanDayEntity>

    @Query("SELECT * FROM plan_day_exercises ORDER BY id")
    suspend fun allPlanDayExercises(): List<PlanExerciseEntity>

    @Query("SELECT * FROM recurrences ORDER BY id")
    suspend fun allRecurrences(): List<RecurrenceEntity>

    @Query("SELECT * FROM scheduled_sessions ORDER BY id")
    suspend fun allScheduledSessions(): List<ScheduledSessionEntity>

    @Query("SELECT * FROM sessions ORDER BY id")
    suspend fun allSessions(): List<SessionEntity>

    @Query("SELECT * FROM session_exercises ORDER BY id")
    suspend fun allSessionExercises(): List<SessionExerciseEntity>

    @Query("SELECT * FROM set_logs ORDER BY id")
    suspend fun allSetLogs(): List<SetLogEntity>

    @Query("SELECT * FROM rest_intervals ORDER BY id")
    suspend fun allRestIntervals(): List<RestIntervalEntity>

    @Query("SELECT * FROM metric_types ORDER BY id")
    suspend fun allMetricTypes(): List<MetricTypeEntity>

    @Query("SELECT * FROM body_entries ORDER BY id")
    suspend fun allBodyEntries(): List<BodyEntryEntity>

    @Query("SELECT * FROM app_meta ORDER BY meta_key")
    suspend fun allAppMeta(): List<AppMetaEntity>

    @Query("SELECT * FROM seed_imports ORDER BY id")
    suspend fun allSeedImports(): List<SeedImportEntity>

    // --- import: stan, którego kopia nie może zastąpić w trakcie treningu ---

    @Query("SELECT COUNT(*) FROM sessions WHERE status = 'active'")
    suspend fun activeSessionCount(): Int

    // --- import, tryb „zastąp”: kasowanie od dzieci do rodziców ---

    @Query("DELETE FROM set_logs")
    suspend fun deleteSetLogs()

    @Query("DELETE FROM rest_intervals")
    suspend fun deleteRestIntervals()

    @Query("DELETE FROM session_exercises")
    suspend fun deleteSessionExercises()

    @Query("DELETE FROM sessions")
    suspend fun deleteSessions()

    @Query("DELETE FROM scheduled_sessions")
    suspend fun deleteScheduledSessions()

    @Query("DELETE FROM recurrences")
    suspend fun deleteRecurrences()

    @Query("DELETE FROM plan_day_exercises")
    suspend fun deletePlanDayExercises()

    @Query("DELETE FROM plan_days")
    suspend fun deletePlanDays()

    @Query("DELETE FROM plans")
    suspend fun deletePlans()

    @Query("DELETE FROM exercise_media")
    suspend fun deleteExerciseMedia()

    @Query("DELETE FROM exercises")
    suspend fun deleteExercises()

    @Query("DELETE FROM body_entries")
    suspend fun deleteBodyEntries()

    @Query("DELETE FROM metric_types")
    suspend fun deleteMetricTypes()

    @Query("DELETE FROM seed_imports")
    suspend fun deleteSeedImports()

    @Query("DELETE FROM app_meta")
    suspend fun deleteAppMeta()

    // --- import: wstawianie (rodzice przed dziećmi) ---

    @Insert
    suspend fun insertAppMeta(rows: List<AppMetaEntity>): List<Long>

    @Insert
    suspend fun insertSeedImports(rows: List<SeedImportEntity>): List<Long>

    @Insert
    suspend fun insertExercises(rows: List<ExerciseEntity>): List<Long>

    @Insert
    suspend fun insertMetricTypes(rows: List<MetricTypeEntity>): List<Long>

    @Insert
    suspend fun insertPlans(rows: List<PlanEntity>): List<Long>

    @Insert
    suspend fun insertPlanDays(rows: List<PlanDayEntity>): List<Long>

    /**
     * Dni planu w trybie „dopisz”: plan o tym samym `external_id` ma już swoje
     * dni, a `(plan_id, position)` jest unikalne - powtórzona pozycja wraca jako
     * `-1` i nie wchodzi (razem ze swoimi ćwiczeniami).
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlanDaysIgnoringDuplicates(rows: List<PlanDayEntity>): List<Long>

    /**
     * Wiersze rozpoznane po kluczu naturalnym - potrzebne w trybie „dopisz”,
     * żeby dowiązać do istniejącego wiersza, zamiast tworzyć drugi taki sam
     * (i gubić powiązania historii).
     *
     * Kolejność ma znaczenie: najpierw `external_id` (identyfikator z katalogu),
     * a dopiero potem nazwa. Wiersz bez `external_id` to wpis użytkownika, więc
     * jedyne, po czym da się go rozpoznać, to nazwa - i lepiej scalić
     * „Przysiad” z „Przysiadem”, niż pokazać go dwa razy.
     */
    @Query("SELECT id FROM exercises WHERE external_id = :externalId LIMIT 1")
    suspend fun exerciseIdByExternalId(externalId: String): Long?

    @Query("SELECT id FROM exercises WHERE name = :name ORDER BY id LIMIT 1")
    suspend fun exerciseIdByName(name: String): Long?

    @Query("SELECT id FROM plans WHERE external_id = :externalId LIMIT 1")
    suspend fun planIdByExternalId(externalId: String): Long?

    @Query("SELECT id FROM plans WHERE name = :name ORDER BY id LIMIT 1")
    suspend fun planIdByName(name: String): Long?

    /** Wskaźnik karty postaci: najpierw wbudowany klucz, potem nazwa. */
    @Query("SELECT id FROM metric_types WHERE builtin_key = :builtinKey LIMIT 1")
    suspend fun metricTypeIdByBuiltinKey(builtinKey: String): Long?

    @Query("SELECT id FROM metric_types WHERE name = :name ORDER BY id LIMIT 1")
    suspend fun metricTypeIdByName(name: String): Long?

    @Insert
    suspend fun insertPlanDayExercises(rows: List<PlanExerciseEntity>): List<Long>

    @Insert
    suspend fun insertRecurrences(rows: List<RecurrenceEntity>): List<Long>

    @Insert
    suspend fun insertSessions(rows: List<SessionEntity>): List<Long>

    @Insert
    suspend fun insertSessionExercises(rows: List<SessionExerciseEntity>): List<Long>

    @Insert
    suspend fun insertSetLogs(rows: List<SetLogEntity>): List<Long>

    @Insert
    suspend fun insertScheduledSessions(rows: List<ScheduledSessionEntity>): List<Long>

    @Insert
    suspend fun insertRestIntervals(rows: List<RestIntervalEntity>): List<Long>

    @Insert
    suspend fun insertExerciseMedia(rows: List<ExerciseMediaEntity>): List<Long>

    @Insert
    suspend fun insertBodyEntries(rows: List<BodyEntryEntity>): List<Long>

    /**
     * Pomiary w trybie „dopisz”: wskaźnik o tej samej nazwie to ten sam
     * wskaźnik, a `(metric_type_id, date)` jest unikalne - pomiar na ten sam
     * dzień już jest w bazie i zostaje bez zmian.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBodyEntriesIgnoringDuplicates(rows: List<BodyEntryEntity>): List<Long>

    /** Metadane w trybie „dopisz”: pary klucz-wartość nadpisują się po kluczu. */
    @Upsert
    suspend fun upsertAppMeta(rows: List<AppMetaEntity>)
}
