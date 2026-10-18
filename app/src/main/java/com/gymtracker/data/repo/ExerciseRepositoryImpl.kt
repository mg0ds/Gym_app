package com.gymtracker.data.repo

import com.gymtracker.data.db.daos.ExerciseDao
import com.gymtracker.data.db.daos.PlanDao
import com.gymtracker.data.db.daos.SessionDao
import com.gymtracker.data.search.ExerciseSearchQuery
import com.gymtracker.domain.model.ArchiveImpact
import com.gymtracker.domain.model.Exercise
import com.gymtracker.domain.model.ExerciseDraft
import com.gymtracker.domain.model.ExerciseUsage
import com.gymtracker.domain.repository.ExerciseRepository
import com.gymtracker.domain.time.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@Singleton
class ExerciseRepositoryImpl @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val planDao: PlanDao,
    private val sessionDao: SessionDao,
    private val timeProvider: TimeProvider,
) : ExerciseRepository {

    override fun observeExercises(
        query: String,
        muscleGroup: String?,
        favouritesOnly: Boolean,
    ): Flow<List<Exercise>> = exerciseDao
        .observeExercises(
            ftsQuery = ExerciseSearchQuery.build(query),
            muscleGroup = muscleGroup,
            favouritesOnly = favouritesOnly,
        )
        .map { entities -> entities.map { it.toDomain() } }

    override fun observeExercise(id: Long): Flow<Exercise?> =
        exerciseDao.observeById(id).map { entity -> entity?.toDomain() }

    override fun observeMuscleGroups(): Flow<List<String>> = exerciseDao.observeMuscleGroups()

    override fun observeActiveCount(): Flow<Int> = exerciseDao.observeActiveCount()

    override fun observeUsage(id: Long): Flow<ExerciseUsage> = combine(
        planDao.observeExerciseUsage(id),
        sessionDao.observeExerciseUsage(id),
    ) { planPositions, sessionPositions ->
        ExerciseUsage(
            planPositions = planPositions,
            sessionPositions = sessionPositions,
        )
    }

    override suspend fun getExercise(id: Long): Exercise? = exerciseDao.getById(id)?.toDomain()

    override suspend fun addExercise(draft: ExerciseDraft): Long =
        exerciseDao.insert(
            draft.toEntity(
                nowEpochMs = timeProvider.nowEpochMs(),
                // Własne ćwiczenia nie mają sluga, więc import ich nie dotknie.
                externalId = null,
            ),
        )

    override suspend fun updateExercise(id: Long, draft: ExerciseDraft) {
        val existing = exerciseDao.getById(id) ?: return
        exerciseDao.update(
            draft.toEntity(
                nowEpochMs = timeProvider.nowEpochMs(),
                id = existing.id,
                externalId = existing.externalId,
                nameInProgram = existing.nameInProgram,
                sourceUrl = existing.sourceUrl,
                muscleGroupSlug = existing.muscleGroupSlug,
                forceType = existing.forceType,
                isFavourite = existing.isFavourite,
                isArchived = existing.isArchived,
                createdAtEpochMs = existing.createdAtEpochMs,
            ),
        )
    }

    override suspend fun setFavourite(id: Long, isFavourite: Boolean) {
        exerciseDao.setFavourite(
            id = id,
            isFavourite = isFavourite,
            updatedAtEpochMs = timeProvider.nowEpochMs(),
        )
    }

    override suspend fun archiveExercise(id: Long) {
        exerciseDao.archive(id = id, updatedAtEpochMs = timeProvider.nowEpochMs())
    }

    override fun observeArchivedExercises(): Flow<List<Exercise>> =
        exerciseDao.observeArchived().map { rows -> rows.map { it.toDomain() } }

    override fun observeArchivedCount(): Flow<Int> = exerciseDao.observeArchivedCount()

    override fun observeArchiveImpact(): Flow<ArchiveImpact> =
        exerciseDao.observeArchiveImpact().map { row -> row.toDomain() }

    override suspend fun restoreExercise(id: Long) {
        exerciseDao.restore(id = id, updatedAtEpochMs = timeProvider.nowEpochMs())
    }

    /**
     * Kasowanie z archiwum jest twarde i **celowo** nie przechodzi przez żadną
     * warstwę pośrednią: kaskada w schemacie wie, co zabrać (pozycje w planach,
     * wpisy w historii, załączniki), a my nie powtarzamy tej wiedzy w Kotlinie.
     * Dlatego nie ma tu też sprzątania miniatur: pliki po skasowanych
     * załącznikach są młode, a `sweepOrphanThumbnails` z założenia nie rusza
     * świeżych plików (M7) - zabierze je dopiero start doby później.
     */
    override suspend fun deleteExercisePermanently(id: Long) {
        exerciseDao.deleteById(id)
    }

    override suspend fun emptyArchive(): Int = exerciseDao.deleteArchived()
}
