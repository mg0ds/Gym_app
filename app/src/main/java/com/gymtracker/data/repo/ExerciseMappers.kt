package com.gymtracker.data.repo

import com.gymtracker.data.db.entities.ExerciseEntity
import com.gymtracker.domain.model.Exercise
import com.gymtracker.domain.model.ExerciseDraft
import com.gymtracker.domain.model.YouTubeLinks
import kotlinx.serialization.json.Json

/** Jedna instancja konwertera JSON dla list zapisywanych w bazie. */
internal val ExerciseJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** Lista mięśni dodatkowych: w bazie leży jako JSON w kolumnie TEXT. */
internal fun encodeMuscles(muscles: List<String>): String? =
    muscles.filter { it.isNotBlank() }.distinct().takeIf { it.isNotEmpty() }?.let { list ->
        ExerciseJson.encodeToString(list)
    }

internal fun decodeMuscles(text: String?): List<String> {
    if (text.isNullOrBlank()) return emptyList()
    return runCatching { ExerciseJson.decodeFromString<List<String>>(text) }.getOrDefault(emptyList())
}

internal fun ExerciseEntity.toDomain(): Exercise = Exercise(
    id = id,
    externalId = externalId,
    name = name,
    nameInProgram = nameInProgram,
    sourceUrl = sourceUrl,
    muscleGroup = muscleGroup,
    muscleGroupSlug = muscleGroupSlug,
    secondaryMuscles = decodeMuscles(secondaryMusclesJson),
    equipment = equipment,
    mechanics = mechanics,
    forceType = forceType,
    level = level,
    youtubeUrl = youtubeUrl,
    defaultSets = defaultSets,
    defaultRepsMin = defaultRepsMin,
    defaultRepsMax = defaultRepsMax,
    defaultRestS = defaultRestS,
    notes = notes,
    isFavourite = isFavourite,
    isArchived = isArchived,
)

/**
 * Formularz → wiersz bazy. Link do filmu normalizujemy tutaj (w jednym miejscu
 * dla dodawania i edycji), a teksty przycinamy, żeby puste pola zostały `null`.
 */
internal fun ExerciseDraft.toEntity(
    nowEpochMs: Long,
    id: Long = 0,
    externalId: String? = null,
    nameInProgram: String? = null,
    sourceUrl: String? = null,
    muscleGroupSlug: String? = null,
    forceType: String? = null,
    isFavourite: Boolean = false,
    isArchived: Boolean = false,
    createdAtEpochMs: Long = nowEpochMs,
): ExerciseEntity = ExerciseEntity(
    id = id,
    externalId = externalId,
    name = name.trim(),
    nameInProgram = nameInProgram,
    sourceUrl = sourceUrl,
    muscleGroup = muscleGroup,
    muscleGroupSlug = muscleGroupSlug,
    secondaryMusclesJson = encodeMuscles(secondaryMuscles),
    equipment = equipment,
    mechanics = mechanics,
    forceType = forceType,
    level = level,
    youtubeUrl = YouTubeLinks.normalizeOrKeepRaw(youtubeUrl),
    defaultSets = defaultSets,
    defaultRepsMin = defaultRepsMin,
    defaultRepsMax = defaultRepsMax,
    defaultRestS = defaultRestS,
    notes = notes?.trim()?.takeIf { it.isNotEmpty() },
    isFavourite = isFavourite,
    isArchived = isArchived,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = nowEpochMs,
)
