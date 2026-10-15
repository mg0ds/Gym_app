package com.gymtracker.data.repo

import com.gymtracker.data.db.daos.ExerciseDao
import com.gymtracker.data.search.ExerciseSearchQuery
import com.gymtracker.domain.model.Exercise
import com.gymtracker.domain.model.ExerciseDraft
import com.gymtracker.domain.repository.ExerciseRepository
import com.gymtracker.domain.time.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class ExerciseRepositoryImpl @Inject constructor(
    private val exerciseDao: ExerciseDao,
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

    override suspend fun deleteExercise(id: Long) {
        exerciseDao.deleteById(id)
    }
}
