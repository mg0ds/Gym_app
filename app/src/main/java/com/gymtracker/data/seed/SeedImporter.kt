package com.gymtracker.data.seed

import com.gymtracker.data.db.daos.AppMetaDao
import com.gymtracker.data.db.daos.ExerciseDao
import com.gymtracker.data.db.daos.PlanDao
import com.gymtracker.data.db.entities.AppMetaEntity
import com.gymtracker.data.db.entities.ExerciseEntity
import com.gymtracker.data.db.entities.PlanDayEntity
import com.gymtracker.data.db.entities.PlanEntity
import com.gymtracker.data.db.entities.PlanExerciseEntity
import com.gymtracker.data.repo.encodeMuscles
import com.gymtracker.domain.model.RepsUnit
import com.gymtracker.domain.model.YouTubeLinks
import com.gymtracker.domain.time.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/**
 * Import danych startowych: katalog ćwiczeń (`assets/seed/exercises.json`)
 * i plany z katalogu `assets/seed/programs/`.
 *
 * Zasady, które muszą zostać nienaruszone:
 *
 *  * wiersze rozpoznajemy po `external_id` (slug) - ponowny import **nie tworzy
 *    duplikatów i nie nadpisuje** tego, co użytkownik zmienił,
 *  * import jest idempotentny: jeśli wersja danych się nie zmieniła, a dane
 *    już są w bazie, nie robimy nic,
 *  * po udanym imporcie zapisujemy wersję seeda w `app_meta`, żeby dało się
 *    wykryć aktualizację danych w kolejnych wydaniach aplikacji,
 *  * plan wskazuje ćwiczenia po slugu, więc **katakog musi być zaimportowany
 *    pierwszy** ([importStartDataIfNeeded] pilnuje tej kolejności).
 */
@Singleton
class SeedImporter @Inject constructor(
    private val seedSource: SeedSource,
    private val exerciseDao: ExerciseDao,
    private val planDao: PlanDao,
    private val appMetaDao: AppMetaDao,
    private val timeProvider: TimeProvider,
) {

    private val json = Json { ignoreUnknownKeys = true }

    // --- katalog ćwiczeń (M1) ---

    /**
     * Importuje katalog, jeśli to potrzebne (pierwsze uruchomienie albo nowa
     * wersja danych). Zwraca `null`, gdy nie było nic do zrobienia.
     */
    suspend fun importExercisesIfNeeded(): SeedImportResult? {
        val seedFile = readExercisesSeed()
        val storedVersion = appMetaDao.get(SeedFiles.META_EXERCISES_VERSION)
        if (storedVersion == seedFile.generatedAt && exerciseDao.count() > 0) return null
        return importExercises(seedFile)
    }

    /** Import na żądanie (np. przycisk w ustawieniach) - zawsze przechodzi przez bazę. */
    suspend fun importExercises(): SeedImportResult = importExercises(readExercisesSeed())

    private suspend fun importExercises(seedFile: ExercisesSeedFile): SeedImportResult {
        val now = timeProvider.nowEpochMs()
        val entities = seedFile.exercises.map { exercise -> exercise.toEntity(now) }

        // -1 oznacza wiersz pominięty (external_id już był w bazie).
        val rowIds = exerciseDao.insertIgnoringDuplicates(entities)
        val imported = rowIds.count { rowId -> rowId != -1L }

        appMetaDao.upsert(
            AppMetaEntity(
                key = SeedFiles.META_EXERCISES_VERSION,
                value = seedFile.generatedAt,
                updatedAtEpochMs = now,
            ),
        )

        return SeedImportResult(
            imported = imported,
            skipped = entities.size - imported,
            totalInSeed = entities.size,
            seedVersion = seedFile.generatedAt,
        )
    }

    // --- plany (M2) ---

    /**
     * Importuje plany startowe, jeśli to potrzebne. Zwraca `null`, gdy nie było
     * nic do zrobienia.
     */
    suspend fun importProgramsIfNeeded(): SeedProgramImportResult? {
        val files = readProgramSeeds()
        if (files.isEmpty()) return null
        val version = versionOf(files)
        val stored = appMetaDao.get(SeedFiles.META_PROGRAMS_VERSION)
        if (stored == version && planDao.countPlans() > 0) return null
        return importPrograms(files, version)
    }

    /** Import planów na żądanie (przycisk w ustawieniach). */
    suspend fun importPrograms(): SeedProgramImportResult {
        val files = readProgramSeeds()
        return importPrograms(files, versionOf(files))
    }

    /**
     * Import przy starcie aplikacji: najpierw katalog (plany wskazują ćwiczenia
     * po slugu), potem plany. Zwraca `false`, gdy nie było nic do zrobienia.
     */
    suspend fun importStartDataIfNeeded(): Boolean {
        val exercises = importExercisesIfNeeded() != null
        val programs = importProgramsIfNeeded() != null
        return exercises || programs
    }

    private suspend fun importPrograms(
        files: List<ProgramSeedFile>,
        version: String,
    ): SeedProgramImportResult {
        val now = timeProvider.nowEpochMs()

        var plansImported = 0
        var plansSkipped = 0
        var daysImported = 0
        var positionsImported = 0
        var alternativesImported = 0
        val missing = mutableListOf<String>()

        files.forEach { file ->
            if (planDao.getPlanByExternalId(file.program.id) != null) {
                plansSkipped++
                return@forEach
            }
            val planId = planDao.insertPlan(
                PlanEntity(
                    externalId = file.program.id,
                    name = file.program.name,
                    sourceUrl = file.program.sourceUrl,
                    author = file.program.author,
                    isActive = false,
                    isArchived = false,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now,
                ),
            )
            plansImported++

            val scheduleByWorkout = file.program.schedule.associateBy { entry -> entry.workoutId }

            file.program.workouts.sortedBy { workout -> workout.order }
                .forEachIndexed { dayIndex, workout ->
                    val dayId = planDao.insertDay(
                        PlanDayEntity(
                            planId = planId,
                            name = workout.name,
                            dayOfWeek = scheduleByWorkout[workout.id]?.dayOfWeek,
                            position = dayIndex + 1,
                        ),
                    )
                    daysImported++

                    val entries = mutableListOf<PlanExerciseEntity>()
                    workout.exercises.sortedBy { exercise -> exercise.order }.forEach { planned ->
                        val group = planned.alternativeGroupKey()
                        val exerciseId = exerciseDao.getActiveByExternalId(planned.exerciseId)?.id
                        if (exerciseId == null) {
                            missing += planned.exerciseId
                        } else {
                            entries += planned.toEntity(
                                dayId = dayId,
                                exerciseId = exerciseId,
                                position = entries.size + 1,
                                alternativeGroup = group,
                                isDefaultChoice = true,
                            )
                            positionsImported++
                        }

                        // Alternatywy wchodzą zaraz po ćwiczeniu podstawowym, w tej
                        // samej grupie - jedno z nich użytkownik faktycznie zrobi.
                        planned.alternatives.forEach { alternative ->
                            val alternativeId =
                                exerciseDao.getActiveByExternalId(alternative.exerciseId)?.id
                            if (alternativeId == null) {
                                missing += alternative.exerciseId
                                return@forEach
                            }
                            entries += planned.toEntity(
                                dayId = dayId,
                                exerciseId = alternativeId,
                                position = entries.size + 1,
                                alternativeGroup = group,
                                isDefaultChoice = false,
                            )
                            positionsImported++
                            alternativesImported++
                        }
                    }

                    if (entries.isNotEmpty()) planDao.insertDayExercises(entries)
                }
        }

        appMetaDao.upsert(
            AppMetaEntity(
                key = SeedFiles.META_PROGRAMS_VERSION,
                value = version,
                updatedAtEpochMs = now,
            ),
        )

        return SeedProgramImportResult(
            plansImported = plansImported,
            plansSkipped = plansSkipped,
            daysImported = daysImported,
            positionsImported = positionsImported,
            alternativesImported = alternativesImported,
            missingExercises = missing.distinct(),
            seedVersion = version,
        )
    }

    private suspend fun readExercisesSeed(): ExercisesSeedFile =
        json.decodeFromString(seedSource.readText(SeedFiles.EXERCISES))

    private suspend fun readProgramSeeds(): List<ProgramSeedFile> =
        seedSource.list(SeedFiles.PROGRAMS_DIR)
            .filter { name -> name.endsWith(".json") }
            .map { name -> json.decodeFromString<ProgramSeedFile>(seedSource.readText("${SeedFiles.PROGRAMS_DIR}/$name")) }

    /** Wersja kilku plików: zmienia się, gdy zmieni się którykolwiek z nich. */
    private fun versionOf(files: List<ProgramSeedFile>): String =
        files.sortedBy { file -> file.program.id }
            .joinToString("|") { file -> "${file.program.id}@${file.generatedAt}" }
}

/** Podsumowanie importu katalogu pokazywane użytkownikowi. */
data class SeedImportResult(
    val imported: Int,
    val skipped: Int,
    val totalInSeed: Int,
    val seedVersion: String,
)

/** Podsumowanie importu planów. */
data class SeedProgramImportResult(
    val plansImported: Int,
    val plansSkipped: Int,
    val daysImported: Int,
    val positionsImported: Int,
    val alternativesImported: Int,
    /** Slugi ćwiczeń, których nie było w katalogu (plan nie mógł ich dodać). */
    val missingExercises: List<String>,
    val seedVersion: String,
)

/** Wiersz danych startowych → wiersz bazy (z normalizacją linku do filmu). */
internal fun SeedExercise.toEntity(nowEpochMs: Long): ExerciseEntity = ExerciseEntity(
    externalId = id,
    name = name,
    nameInProgram = nameInProgram,
    sourceUrl = sourceUrl,
    muscleGroup = muscleGroup,
    muscleGroupSlug = muscleGroupSlug,
    secondaryMusclesJson = encodeMuscles(secondaryMuscles),
    equipment = equipment,
    mechanics = mechanics,
    level = level,
    youtubeUrl = YouTubeLinks.idOf(youtubeId)?.let(YouTubeLinks::watchUrl),
    isFavourite = false,
    isArchived = false,
    createdAtEpochMs = nowEpochMs,
    updatedAtEpochMs = nowEpochMs,
)

/** Nazwa grupy „X albo Y” albo `null`, gdy ćwiczenie nie ma alternatyw. */
internal fun SeedWorkoutExercise.alternativeGroupKey(): String? =
    if (alternatives.isEmpty()) {
        null
    } else {
        (listOf(exerciseId) + alternatives.map { alternative -> alternative.exerciseId })
            .joinToString("-or-")
    }

/** Wiersz planu z seeda → wiersz bazy. */
internal fun SeedWorkoutExercise.toEntity(
    dayId: Long,
    exerciseId: Long,
    position: Int,
    alternativeGroup: String?,
    isDefaultChoice: Boolean,
): PlanExerciseEntity = PlanExerciseEntity(
    planDayId = dayId,
    exerciseId = exerciseId,
    position = position,
    targetSets = sets,
    targetRepsMin = repsMin,
    targetRepsMax = repsMax,
    targetRepsUnit = repsUnit().wire,
    targetWeightKg = null,
    restSeconds = null,
    supersetGroup = null,
    alternativeGroup = alternativeGroup,
    isDefaultChoice = isDefaultChoice,
    note = if (isDefaultChoice) modifier?.trim()?.takeIf { it.isNotEmpty() } else null,
)

/** Serie na czas (`tracking.reps == "time"`) zamieniamy na jednostkę `seconds`. */
private fun SeedWorkoutExercise.repsUnit(): RepsUnit =
    if (tracking?.reps == "time") {
        RepsUnit.fromWire(tracking.unit ?: RepsUnit.SECONDS.wire)
    } else {
        RepsUnit.REPS
    }
