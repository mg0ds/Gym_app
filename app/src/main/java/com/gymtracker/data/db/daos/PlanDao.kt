package com.gymtracker.data.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.gymtracker.data.db.entities.PlanDayEntity
import com.gymtracker.data.db.entities.PlanEntity
import com.gymtracker.data.db.entities.PlanExerciseEntity
import com.gymtracker.data.db.rows.PlanDayOptionRow
import com.gymtracker.data.db.rows.PlanExerciseRow
import kotlinx.coroutines.flow.Flow

/**
 * Plany treningowe, ich dni i ćwiczenia dni.
 *
 * Uwagi do kolejności ([position]):
 *
 * * `plan_days` ma `UNIQUE(plan_id, position)`, więc przestawianie dni idzie
 *   dwoma przebiegami ([setDayPosition] z wartościami tymczasowymi, potem
 *   docelowymi) - robi to `PlanRepositoryImpl` w jednej transakcji,
 * * `plan_day_exercises` nie ma takiego ograniczenia, ale i tak numerujemy
 *   pozycje gęsto (1, 2, 3...), żeby przeciąganie miało proste reguły.
 */
@Dao
interface PlanDao {

    @Query("SELECT * FROM plans WHERE is_archived = 0 ORDER BY name COLLATE NOCASE")
    fun observePlans(): Flow<List<PlanEntity>>

    @Query("SELECT * FROM plans WHERE id = :planId")
    fun observePlan(planId: Long): Flow<PlanEntity?>

    @Query("SELECT * FROM plans WHERE id = :planId")
    suspend fun getPlan(planId: Long): PlanEntity?

    @Query("SELECT * FROM plans WHERE external_id = :externalId LIMIT 1")
    suspend fun getPlanByExternalId(externalId: String): PlanEntity?

    @Query("SELECT COUNT(*) FROM plans")
    suspend fun countPlans(): Int

    /**
     * Liczba pozycji danego ćwiczenia we wszystkich planach - używane przy
     * usuwaniu ćwiczenia, żeby powiedzieć użytkownikowi, co zniknie z planów.
     */
    @Query("SELECT COUNT(*) FROM plan_day_exercises WHERE exercise_id = :exerciseId")
    fun observeExerciseUsage(exerciseId: Long): Flow<Int>

    @Insert
    suspend fun insertPlan(plan: PlanEntity): Long

    @Update
    suspend fun updatePlan(plan: PlanEntity)

    @Query("DELETE FROM plans WHERE id = :planId")
    suspend fun deletePlan(planId: Long)

    // --- dni planu ---

    @Query("SELECT * FROM plan_days WHERE plan_id = :planId ORDER BY position")
    fun observeDays(planId: Long): Flow<List<PlanDayEntity>>

    /**
     * Wszystkie dni niearchiwalnych planów z nazwą planu i liczbą ćwiczeń -
     * lista wyboru treningu na konkretny dzień w kalendarzu. Jedno zapytanie
     * zamiast pytania o dni każdego planu osobno.
     */
    @Query(
        """
        SELECT pd.id AS day_id, pd.name AS day_name, p.name AS plan_name,
               (SELECT COUNT(*) FROM plan_day_exercises pde WHERE pde.plan_day_id = pd.id)
                   AS exercise_count
        FROM plan_days pd
        JOIN plans p ON p.id = pd.plan_id
        WHERE p.is_archived = 0
        ORDER BY p.name COLLATE NOCASE, pd.position
        """,
    )
    fun observeDayOptions(): Flow<List<PlanDayOptionRow>>

    @Query("SELECT * FROM plan_days WHERE id = :dayId")
    fun observeDay(dayId: Long): Flow<PlanDayEntity?>

    @Query("SELECT * FROM plan_days WHERE id = :dayId")
    suspend fun getDay(dayId: Long): PlanDayEntity?

    @Query("SELECT * FROM plan_days WHERE plan_id = :planId ORDER BY position")
    suspend fun getDays(planId: Long): List<PlanDayEntity>

    @Query("SELECT COALESCE(MAX(position), 0) FROM plan_days WHERE plan_id = :planId")
    suspend fun maxDayPosition(planId: Long): Int

    @Insert
    suspend fun insertDay(day: PlanDayEntity): Long

    @Update
    suspend fun updateDay(day: PlanDayEntity)

    @Query("DELETE FROM plan_days WHERE id = :dayId")
    suspend fun deleteDay(dayId: Long)

    @Query("UPDATE plan_days SET position = :position WHERE id = :dayId")
    suspend fun setDayPosition(dayId: Long, position: Int)

    // --- ćwiczenia dnia ---

    @Query(
        """
        SELECT pde.*, e.name AS exercise_name, e.muscle_group AS exercise_muscle_group,
               e.equipment AS exercise_equipment, e.tracking AS exercise_tracking
        FROM plan_day_exercises pde
        JOIN exercises e ON e.id = pde.exercise_id
        WHERE pde.plan_day_id = :dayId
        ORDER BY pde.position
        """,
    )
    fun observeDayExercises(dayId: Long): Flow<List<PlanExerciseRow>>

    @Query(
        """
        SELECT pde.*, e.name AS exercise_name, e.muscle_group AS exercise_muscle_group,
               e.equipment AS exercise_equipment, e.tracking AS exercise_tracking
        FROM plan_day_exercises pde
        JOIN plan_days pd ON pd.id = pde.plan_day_id
        JOIN exercises e ON e.id = pde.exercise_id
        WHERE pd.plan_id = :planId
        ORDER BY pd.position, pde.position
        """,
    )
    fun observePlanExercises(planId: Long): Flow<List<PlanExerciseRow>>

    @Query(
        """
        SELECT pde.*, e.name AS exercise_name, e.muscle_group AS exercise_muscle_group,
               e.equipment AS exercise_equipment, e.tracking AS exercise_tracking
        FROM plan_day_exercises pde
        JOIN exercises e ON e.id = pde.exercise_id
        WHERE pde.plan_day_id = :dayId
        ORDER BY pde.position
        """,
    )
    suspend fun getDayExercises(dayId: Long): List<PlanExerciseRow>

    @Query("SELECT COALESCE(MAX(position), 0) FROM plan_day_exercises WHERE plan_day_id = :dayId")
    suspend fun maxExercisePosition(dayId: Long): Int

    @Query("SELECT * FROM plan_day_exercises WHERE id = :id")
    suspend fun getDayExercise(id: Long): PlanExerciseEntity?

    @Insert
    suspend fun insertDayExercise(entry: PlanExerciseEntity): Long

    @Insert
    suspend fun insertDayExercises(entries: List<PlanExerciseEntity>): List<Long>

    @Update
    suspend fun updateDayExercise(entry: PlanExerciseEntity)

    @Query("UPDATE plan_day_exercises SET position = :position WHERE id = :id")
    suspend fun setExercisePosition(id: Long, position: Int)

    @Query("DELETE FROM plan_day_exercises WHERE id = :id")
    suspend fun deleteDayExercise(id: Long)
}
