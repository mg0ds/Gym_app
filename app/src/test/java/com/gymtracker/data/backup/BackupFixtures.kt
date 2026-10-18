package com.gymtracker.data.backup

import com.gymtracker.data.db.AppDatabase
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

/** Identyfikatory wierszy założonych przez [fillBackupData]. */
internal data class SeededIds(
    val squatId: Long,
    val benchId: Long,
    val planId: Long,
    val planDayId: Long,
    val planExerciseId: Long,
    val alternativeId: Long,
    val recurrenceId: Long,
    val scheduledId: Long,
    val sessionId: Long,
    val sessionExerciseId: Long,
    val setLogId: Long,
    val restId: Long,
    val metricTypeId: Long,
    val bodyEntryId: Long,
    val mediaId: Long,
) {
    companion object {
        /** Ścieżka miniatury zapisana w wierszu - plik zakłada test serwisu. */
        const val THUMBNAIL_PATH = "thumbs/squat.jpg"
    }
}

/**
 * Zakłada w bazie po jednym wierszu **każdej** tabeli, którą zna kopia.
 *
 * Kompletność jest tu istotą testu: kopia, która gubi jedną tabelę, przechodzi
 * każdy test częściowy i psuje się dopiero u użytkownika. Dlatego fixture
 * zakłada też wiersze „niewygodne”: rekord z `SET NULL` (przerwa po skasowaniu
 * serii), wpis kalendarza wskazujący kilka tabel naraz i załącznik z miniaturą.
 */
internal suspend fun fillBackupData(db: AppDatabase, suffix: String = ""): SeededIds {
    val dao = db.backupDao()

    val squatId = dao.insertExercises(
        listOf(
            ExerciseEntity(
                externalId = "barbell-back-squat$suffix",
                name = "Barbell Back Squat$suffix",
                muscleGroup = "Nogi",
                equipment = "Sztanga",
                defaultSets = 5,
                defaultRepsMin = 5,
                defaultRepsMax = 5,
                defaultRestS = 180,
                createdAtEpochMs = 1_000L,
                updatedAtEpochMs = 1_000L,
            ),
        ),
    ).single()

    val benchId = dao.insertExercises(
        listOf(
            ExerciseEntity(
                externalId = "bench-press$suffix",
                name = "Bench Press$suffix",
                muscleGroup = "Klatka piersiowa",
                isFavourite = true,
                createdAtEpochMs = 1_000L,
                updatedAtEpochMs = 1_000L,
            ),
        ),
    ).single()

    val planId = dao.insertPlans(
        listOf(
            PlanEntity(
                externalId = "full-body$suffix",
                name = "Full Body$suffix",
                description = "Trzy dni w tygodniu",
                isActive = true,
                createdAtEpochMs = 1_000L,
                updatedAtEpochMs = 1_000L,
            ),
        ),
    ).single()

    val planDayId = dao.insertPlanDays(
        listOf(
            PlanDayEntity(planId = planId, name = "Workout A$suffix", dayOfWeek = 0, position = 0),
        ),
    ).single()

    val planExerciseId = dao.insertPlanDayExercises(
        listOf(
            PlanExerciseEntity(
                planDayId = planDayId,
                exerciseId = squatId,
                position = 0,
                targetSets = 5,
                targetRepsMin = 5,
                targetRepsMax = 5,
                restSeconds = 180,
            ),
        ),
    ).single()

    val alternativeId = dao.insertPlanDayExercises(
        listOf(
            PlanExerciseEntity(
                planDayId = planDayId,
                exerciseId = benchId,
                position = 1,
                alternativeGroup = "a",
                isDefaultChoice = false,
            ),
        ),
    ).single()

    val recurrenceId = dao.insertRecurrences(
        listOf(
            RecurrenceEntity(
                planId = planId,
                planDayId = planDayId,
                startDate = "2026-10-01",
                endDate = "2026-11-01",
                intervalDays = 2,
                createdAtEpochMs = 1_000L,
            ),
        ),
    ).single()

    val sessionId = dao.insertSessions(
        listOf(
            SessionEntity(
                planId = planId,
                planDayId = planDayId,
                name = "Workout A$suffix",
                startedAtEpochMs = 100_000L,
                finishedAtEpochMs = 200_000L,
                status = SessionEntity.STATUS_FINISHED,
                pausedTotalMs = 5_000L,
            ),
        ),
    ).single()

    val scheduledId = dao.insertScheduledSessions(
        listOf(
            ScheduledSessionEntity(
                date = "2026-10-05",
                planId = planId,
                planDayId = planDayId,
                recurrenceId = recurrenceId,
                title = "Workout A$suffix",
                status = "done",
                sessionId = sessionId,
                createdAtEpochMs = 1_000L,
            ),
        ),
    ).single()

    val sessionExerciseId = dao.insertSessionExercises(
        listOf(
            SessionExerciseEntity(
                sessionId = sessionId,
                exerciseId = squatId,
                position = 0,
                targetSets = 5,
                targetRepsMin = 5,
                targetRepsMax = 5,
                restSeconds = 180,
            ),
        ),
    ).single()

    val setLogId = dao.insertSetLogs(
        listOf(
            SetLogEntity(
                sessionExerciseId = sessionExerciseId,
                setIndex = 0,
                weightKg = 100.0,
                reps = 5,
                rpe = 8.0,
                restAfterS = 170,
                restTargetS = 180,
                completedAtEpochMs = 150_000L,
            ),
        ),
    ).single()

    val restId = dao.insertRestIntervals(
        listOf(
            RestIntervalEntity(
                exerciseId = squatId,
                sessionId = sessionId,
                setLogId = setLogId,
                startedAtEpochMs = 150_000L,
                endedAtEpochMs = 240_000L,
                plannedSeconds = 180,
                actualSeconds = 90,
                endedReason = "completed",
                isAuto = true,
            ),
        ),
    ).single()

    val metricTypeId = dao.insertMetricTypes(
        listOf(
            MetricTypeEntity(
                name = "Waga$suffix",
                unit = "kg",
                direction = "down",
                target = 80.0,
                builtinKey = "weight",
                position = 0,
                createdAtEpochMs = 1_000L,
            ),
        ),
    ).single()

    val bodyEntryId = dao.insertBodyEntries(
        listOf(
            BodyEntryEntity(
                metricTypeId = metricTypeId,
                date = "2026-10-01",
                measuredAtEpochMs = 90_000L,
                value = 82.5,
            ),
        ),
    ).single()

    val mediaId = dao.insertExerciseMedia(
        listOf(
            ExerciseMediaEntity(
                exerciseId = squatId,
                kind = "image",
                remoteUri = "content://media/external/images/media/42",
                thumbnailPath = SeededIds.THUMBNAIL_PATH,
                caption = "Przysiad",
                position = 0,
                createdAtEpochMs = 1_000L,
            ),
        ),
    ).single()

    dao.insertAppMeta(
        listOf(
            AppMetaEntity("exercises_seed_version", "2026-10-15", 1_000L),
            AppMetaEntity("programs_seed_version", "2026-10-15", 1_000L),
        ),
    )

    dao.insertSeedImports(
        listOf(
            SeedImportEntity(
                seedId = "assets/seed/exercises.json",
                version = "2026-10-15",
                checksum = null,
                importedAtEpochMs = 1_000L,
                statsJson = """{"status":"done","imported":23,"skipped":0,"total":23}""",
            ),
        ),
    )

    return SeededIds(
        squatId = squatId,
        benchId = benchId,
        planId = planId,
        planDayId = planDayId,
        planExerciseId = planExerciseId,
        alternativeId = alternativeId,
        recurrenceId = recurrenceId,
        scheduledId = scheduledId,
        sessionId = sessionId,
        sessionExerciseId = sessionExerciseId,
        setLogId = setLogId,
        restId = restId,
        metricTypeId = metricTypeId,
        bodyEntryId = bodyEntryId,
        mediaId = mediaId,
    )
}

/** Wszystkie tabele w postaci, w jakiej trafiają do kopii. */
internal suspend fun readAllTables(db: AppDatabase): BackupTables =
    BackupExporter(db.backupDao()).collect()
