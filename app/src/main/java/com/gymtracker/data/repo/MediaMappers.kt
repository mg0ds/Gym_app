package com.gymtracker.data.repo

import com.gymtracker.data.db.entities.ExerciseMediaEntity
import com.gymtracker.domain.model.ExerciseMedia
import com.gymtracker.domain.model.MediaKind

/**
 * Wiersz bazy na model.
 *
 * Wiersz z nieznanym rodzajem pomijamy (`null`), zamiast zgadywać, co to jest:
 * lepiej, żeby taki załącznik zniknął z listy, niż żeby aplikacja się wywróciła
 * na danych z nowszej wersji bazy.
 */
internal fun ExerciseMediaEntity.toDomainOrNull(): ExerciseMedia? {
    val mediaKind = MediaKind.fromDb(kind) ?: return null
    return ExerciseMedia(
        id = id,
        exerciseId = exerciseId,
        kind = mediaKind,
        remoteUri = remoteUri,
        thumbnailPath = thumbnailPath,
        caption = caption,
        durationMs = durationMs,
        sizeBytes = sizeBytes,
        position = position,
    )
}
