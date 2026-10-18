package com.gymtracker.data.repo

import com.gymtracker.data.db.entities.BodyEntryEntity
import com.gymtracker.data.db.entities.MetricTypeEntity
import com.gymtracker.domain.model.BodyEntry
import com.gymtracker.domain.model.BuiltinMetric
import com.gymtracker.domain.model.MetricDirection
import com.gymtracker.domain.model.MetricType
import java.time.LocalDate

/**
 * Wiersze karty postaci → modele domenowe (M8).
 *
 * Data pomiaru jest w bazie tekstem `YYYY-MM-DD` (tak jak w kalendarzu), więc
 * mapowanie to zwykłe `LocalDate.parse` - bez strefy, bez `Instant`. Datę
 * pomiaru wybiera użytkownik, a nie zegar, więc nie ma czego przeliczać.
 */
internal fun MetricTypeEntity.toDomain(): MetricType = MetricType(
    id = id,
    name = name,
    unit = unit,
    direction = MetricDirection.fromWire(direction),
    target = target,
    builtin = BuiltinMetric.fromWire(builtinKey),
    isArchived = isArchived,
    position = position,
)

internal fun BodyEntryEntity.toDomain(): BodyEntry = BodyEntry(
    id = id,
    metricTypeId = metricTypeId,
    date = LocalDate.parse(date),
    measuredAtEpochMs = measuredAtEpochMs,
    value = value,
    note = note,
)
