package com.gymtracker.data.seed

import com.gymtracker.data.db.daos.AppMetaDao
import com.gymtracker.data.db.daos.ExerciseDao
import com.gymtracker.data.db.entities.AppMetaEntity
import com.gymtracker.data.db.entities.ExerciseEntity
import com.gymtracker.data.repo.encodeMuscles
import com.gymtracker.domain.model.YouTubeLinks
import com.gymtracker.domain.time.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/**
 * Import danych startowych (katalog ćwiczeń z `assets/seed/exercises.json`).
 *
 * Zasady, które muszą zostać nienaruszone:
 *
 *  * wiersze rozpoznajemy po `external_id` (slug) - ponowny import **nie tworzy
 *    duplikatów i nie nadpisuje** tego, co użytkownik zmienił,
 *  * import jest idempotentny: jeśli wersja danych się nie zmieniła, a katalog
 *    nie jest pusty, nie robimy nic,
 *  * po udanym imporcie zapisujemy wersję seeda w `app_meta`, żeby dało się
 *    wykryć aktualizację danych w kolejnych wydaniach aplikacji.
 */
@Singleton
class SeedImporter @Inject constructor(
    private val seedSource: SeedSource,
    private val exerciseDao: ExerciseDao,
    private val appMetaDao: AppMetaDao,
    private val timeProvider: TimeProvider,
) {

    private val json = Json { ignoreUnknownKeys = true }

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

    private suspend fun readExercisesSeed(): ExercisesSeedFile =
        json.decodeFromString(seedSource.readText(SeedFiles.EXERCISES))
}

/** Podsumowanie importu pokazywane użytkownikowi. */
data class SeedImportResult(
    val imported: Int,
    val skipped: Int,
    val totalInSeed: Int,
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
