package com.gymtracker.data.repo

import com.gymtracker.data.db.entities.ExerciseEntity
import com.gymtracker.domain.model.Exercise
import com.gymtracker.domain.model.ExerciseDraft
import com.gymtracker.domain.model.InstructionSteps
import com.gymtracker.domain.model.YouTubeLinks
import kotlinx.serialization.json.Json

/** Jedna instancja konwertera JSON dla list zapisywanych w bazie. */
internal val ExerciseJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** Lista mięśni dodatkowych: w bazie leży jako JSON w kolumnie TEXT. */
internal fun encodeMuscles(muscles: List<String>): String? =
    encodeStringList(muscles.map { it.trim() }.filter { it.isNotEmpty() }.distinct())

internal fun decodeMuscles(text: String?): List<String> = decodeStringList(text)

/**
 * Lista kroków wykonania, też jako JSON. Normalizujemy przy zapisie i odczycie:
 * przy zapisie sprzątamy to, co wpisał użytkownik (puste linie, numeracja),
 * a przy odczycie nie ufamy zawartości bazy na tyle, żeby wpuścić do UI puste
 * kroki.
 */
internal fun encodeInstructions(steps: List<String>): String? =
    encodeStringList(InstructionSteps.normalize(steps))

internal fun decodeInstructions(text: String?): List<String> =
    InstructionSteps.normalize(decodeStringList(text))

/** Lista tekstów w kolumnie TEXT jako JSON (mięśnie dodatkowe, kroki). */
private fun encodeStringList(values: List<String>): String? =
    values.takeIf { it.isNotEmpty() }?.let { list -> ExerciseJson.encodeToString(list) }

private fun decodeStringList(text: String?): List<String> {
    if (text.isNullOrBlank()) return emptyList()
    return runCatching { ExerciseJson.decodeFromString<List<String>>(text) }
        .getOrDefault(emptyList())
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
    instructions = decodeInstructions(instructions),
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
    instructions = encodeInstructions(instructions),
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
