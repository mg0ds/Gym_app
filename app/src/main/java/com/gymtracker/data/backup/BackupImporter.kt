package com.gymtracker.data.backup

import androidx.room.withTransaction
import com.gymtracker.data.db.AppDatabase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zbieranie danych do kopii - czyta wszystkie tabele w kolejności, w jakiej
 * siedzą w bazie (po `id`), żeby plik był deterministyczny i dał się porównać
 * między dwoma eksportami bez zmian w danych.
 */
@Singleton
class BackupExporter @Inject constructor(
    private val dao: BackupDao,
) {

    suspend fun collect(): BackupTables = BackupTables(
        exercises = dao.allExercises(),
        exerciseMedia = dao.allExerciseMedia(),
        plans = dao.allPlans(),
        planDays = dao.allPlanDays(),
        planDayExercises = dao.allPlanDayExercises(),
        recurrences = dao.allRecurrences(),
        scheduledSessions = dao.allScheduledSessions(),
        sessions = dao.allSessions(),
        sessionExercises = dao.allSessionExercises(),
        setLogs = dao.allSetLogs(),
        restIntervals = dao.allRestIntervals(),
        metricTypes = dao.allMetricTypes(),
        bodyEntries = dao.allBodyEntries(),
        appMeta = dao.allAppMeta(),
        seedImports = dao.allSeedImports(),
    )

    fun payload(
        tables: BackupTables,
        schemaVersion: Int,
        appVersion: String,
        nowEpochMs: Long,
    ): BackupPayload = BackupPayload(
        schemaVersion = schemaVersion,
        appVersion = appVersion,
        exportedAtEpochMs = nowEpochMs,
        tables = tables,
    )
}

/**
 * Wczytywanie kopii do bazy.
 *
 * **Całość w jednej transakcji.** Albo wchodzi wszystko, albo nic - do bazy
 * nie może trafić połowa kopii, bo po takim imporcie nie da się już odróżnić
 * danych użytkownika od resztek po błędzie (a `schema_version` mówi tylko,
 * z jakiej wersji plik pochodzi, nie jak daleko zaszedł import).
 *
 * **Dwa tryby, jedna zasada dla powiązań.** W trybie „zastąp” wiersze
 * zachowują swoje identyfikatory, więc powiązania zostają takie, jak były.
 * W trybie „dopisz” identyfikatory są nowe, więc każdy wiersz dostaje
 * przepisane klucze obce, a wiersz rozpoznany po kluczu naturalnym (ćwiczenie
 * albo plan o tym samym `external_id`, a gdy go nie ma - o tej samej nazwie,
 * wskaźnik karty postaci po kluczu wbudowanym albo nazwie) **nie tworzy
 * drugiego takiego samego wiersza**: wchodzi pod identyfikatorem tego, który
 * już jest. Historia wczytanego treningu dowiązuje się wtedy do ćwiczenia
 * użytkownika, zamiast zniknąć bez śladu, a karta postaci nie pokazuje dwóch
 * takich samych wskaźników.
 *
 * Gdy powiązać nie ma do czego (np. plan ma już dzień o tej samej pozycji),
 * wiersz zależny idzie za rodzicem tak, jak mówi schemat: przy
 * `ON DELETE CASCADE` znika, przy `ON DELETE SET NULL` zostaje bez powiązania.
 * Innej reguły nie ma - dzięki temu zachowanie „dopisz” pokrywa się
 * z zachowaniem bazy przy kasowaniu.
 *
 * **Czego nie wczytujemy w trybie „dopisz”.** `app_meta` idzie przez `UPSERT`
 * (pary klucz-wartość nie mają sensu jako duplikaty), a `seed_imports` dopisują
 * się jako kolejne wpisy dziennika.
 */
@Singleton
class BackupImporter @Inject constructor(
    private val db: AppDatabase,
    private val dao: BackupDao,
) {

    suspend fun import(tables: BackupTables, mode: BackupImportMode): BackupImportSummary =
        db.withTransaction {
            // W trakcie treningu w pamięci siedzi stan (timer, przerwa, lista
            // ćwiczeń), który wskazuje na wiersze obecnej bazy. Po podmianie
            // danych te wiersze by nie istniały, więc odmawiamy zamiast
            // doprowadzać aplikację do stanu, którego nie ma jak posprzątać.
            if (dao.activeSessionCount() > 0) {
                throw BackupImportException(BackupImportError.WORKOUT_IN_PROGRESS)
            }

            if (mode == BackupImportMode.REPLACE) clearAll()

            val counters = Counters()

            val (exerciseRows, exerciseExisting) = splitExisting(
                rows = tables.exercises,
                mode = mode,
                oldId = { exercise -> exercise.id },
            ) { exercise ->
                exercise.externalId?.let { externalId -> dao.exerciseIdByExternalId(externalId) }
                    ?: dao.exerciseIdByName(exercise.name)
            }
            counters.reused += exerciseExisting.size

            val exercises = copyRows(
                rows = exerciseRows,
                mode = mode,
                counters = counters,
                oldId = { exercise -> exercise.id },
                withId = { exercise, id -> exercise.copy(id = id) },
                insert = dao::insertExercises,
            )
            exercises.putAll(exerciseExisting)

            val (metricTypeRows, metricTypeExisting) = splitExisting(
                rows = tables.metricTypes,
                mode = mode,
                oldId = { type -> type.id },
            ) { type ->
                type.builtinKey?.let { key -> dao.metricTypeIdByBuiltinKey(key) }
                    ?: dao.metricTypeIdByName(type.name)
            }
            counters.reused += metricTypeExisting.size

            val metricTypes = copyRows(
                rows = metricTypeRows,
                mode = mode,
                counters = counters,
                oldId = { type -> type.id },
                withId = { type, id -> type.copy(id = id) },
                insert = dao::insertMetricTypes,
            )
            metricTypes.putAll(metricTypeExisting)

            val (planRows, planExisting) = splitExisting(
                rows = tables.plans,
                mode = mode,
                oldId = { plan -> plan.id },
            ) { plan ->
                plan.externalId?.let { externalId -> dao.planIdByExternalId(externalId) }
                    ?: dao.planIdByName(plan.name)
            }
            counters.reused += planExisting.size

            val plans = copyRows(
                rows = planRows,
                mode = mode,
                counters = counters,
                oldId = { plan -> plan.id },
                withId = { plan, id -> plan.copy(id = id) },
                insert = dao::insertPlans,
            )
            plans.putAll(planExisting)

            val planDayRows = tables.planDays.mapNotNull { day ->
                val planId = plans.get(day.planId) ?: return@mapNotNull null
                day.copy(planId = planId)
            }
            counters.skipped += tables.planDays.size - planDayRows.size

            val planDays = copyRows(
                rows = planDayRows,
                mode = mode,
                counters = counters,
                oldId = { day -> day.id },
                withId = { day, id -> day.copy(id = id) },
                insert = { rows ->
                    if (mode == BackupImportMode.APPEND) {
                        dao.insertPlanDaysIgnoringDuplicates(rows)
                    } else {
                        dao.insertPlanDays(rows)
                    }
                },
            )

            val planExerciseRows = tables.planDayExercises.mapNotNull { entry ->
                val dayId = planDays.get(entry.planDayId) ?: return@mapNotNull null
                val exerciseId = exercises.get(entry.exerciseId) ?: return@mapNotNull null
                entry.copy(planDayId = dayId, exerciseId = exerciseId)
            }
            counters.skipped += tables.planDayExercises.size - planExerciseRows.size

            copyRows(
                rows = planExerciseRows,
                mode = mode,
                counters = counters,
                oldId = { entry -> entry.id },
                withId = { entry, id -> entry.copy(id = id) },
                insert = dao::insertPlanDayExercises,
            )

            val recurrenceRows = tables.recurrences.map { recurrence ->
                recurrence.copy(
                    planId = plans.get(recurrence.planId),
                    planDayId = planDays.get(recurrence.planDayId),
                )
            }

            val recurrences = copyRows(
                rows = recurrenceRows,
                mode = mode,
                counters = counters,
                oldId = { recurrence -> recurrence.id },
                withId = { recurrence, id -> recurrence.copy(id = id) },
                insert = dao::insertRecurrences,
            )

            val sessionRows = tables.sessions.map { session ->
                session.copy(
                    planId = plans.get(session.planId),
                    planDayId = planDays.get(session.planDayId),
                )
            }

            val sessions = copyRows(
                rows = sessionRows,
                mode = mode,
                counters = counters,
                oldId = { session -> session.id },
                withId = { session, id -> session.copy(id = id) },
                insert = dao::insertSessions,
            )

            val sessionExerciseRows = tables.sessionExercises.mapNotNull { entry ->
                val sessionId = sessions.get(entry.sessionId) ?: return@mapNotNull null
                val exerciseId = exercises.get(entry.exerciseId) ?: return@mapNotNull null
                entry.copy(sessionId = sessionId, exerciseId = exerciseId)
            }
            counters.skipped += tables.sessionExercises.size - sessionExerciseRows.size

            val sessionExercises = copyRows(
                rows = sessionExerciseRows,
                mode = mode,
                counters = counters,
                oldId = { entry -> entry.id },
                withId = { entry, id -> entry.copy(id = id) },
                insert = dao::insertSessionExercises,
            )

            val setLogRows = tables.setLogs.mapNotNull { set ->
                val entryId = sessionExercises.get(set.sessionExerciseId) ?: return@mapNotNull null
                set.copy(sessionExerciseId = entryId)
            }
            counters.skipped += tables.setLogs.size - setLogRows.size

            val setLogs = copyRows(
                rows = setLogRows,
                mode = mode,
                counters = counters,
                oldId = { set -> set.id },
                withId = { set, id -> set.copy(id = id) },
                insert = dao::insertSetLogs,
            )

            val restRows = tables.restIntervals.mapNotNull { rest ->
                val exerciseId = exercises.get(rest.exerciseId) ?: return@mapNotNull null
                rest.copy(
                    exerciseId = exerciseId,
                    sessionId = sessions.get(rest.sessionId),
                    setLogId = setLogs.get(rest.setLogId),
                )
            }
            counters.skipped += tables.restIntervals.size - restRows.size

            copyRows(
                rows = restRows,
                mode = mode,
                counters = counters,
                oldId = { rest -> rest.id },
                withId = { rest, id -> rest.copy(id = id) },
                insert = dao::insertRestIntervals,
            )

            val mediaRows = tables.exerciseMedia.mapNotNull { media ->
                val exerciseId = exercises.get(media.exerciseId) ?: return@mapNotNull null
                media.copy(exerciseId = exerciseId)
            }
            counters.skipped += tables.exerciseMedia.size - mediaRows.size

            copyRows(
                rows = mediaRows,
                mode = mode,
                counters = counters,
                oldId = { media -> media.id },
                withId = { media, id -> media.copy(id = id) },
                insert = dao::insertExerciseMedia,
            )

            val scheduledRows = tables.scheduledSessions.map { scheduled ->
                scheduled.copy(
                    planId = plans.get(scheduled.planId),
                    planDayId = planDays.get(scheduled.planDayId),
                    recurrenceId = recurrences.get(scheduled.recurrenceId),
                    sessionId = sessions.get(scheduled.sessionId),
                )
            }

            copyRows(
                rows = scheduledRows,
                mode = mode,
                counters = counters,
                oldId = { scheduled -> scheduled.id },
                withId = { scheduled, id -> scheduled.copy(id = id) },
                insert = dao::insertScheduledSessions,
            )

            val bodyRows = tables.bodyEntries.mapNotNull { entry ->
                val typeId = metricTypes.get(entry.metricTypeId) ?: return@mapNotNull null
                entry.copy(metricTypeId = typeId)
            }
            counters.skipped += tables.bodyEntries.size - bodyRows.size

            copyRows(
                rows = bodyRows,
                mode = mode,
                counters = counters,
                oldId = { entry -> entry.id },
                withId = { entry, id -> entry.copy(id = id) },
                insert = { rows ->
                    if (mode == BackupImportMode.APPEND) {
                        dao.insertBodyEntriesIgnoringDuplicates(rows)
                    } else {
                        dao.insertBodyEntries(rows)
                    }
                },
            )

            if (mode == BackupImportMode.APPEND) {
                dao.upsertAppMeta(tables.appMeta)
                counters.inserted += tables.appMeta.size
            } else {
                counters.inserted += dao.insertAppMeta(tables.appMeta).size
            }

            copyRows(
                rows = tables.seedImports,
                mode = mode,
                counters = counters,
                oldId = { entry -> entry.id },
                withId = { entry, id -> entry.copy(id = id) },
                insert = dao::insertSeedImports,
            )

            BackupImportSummary(
                mode = mode,
                inserted = counters.inserted,
                reused = counters.reused,
                skipped = counters.skipped,
            )
        }

    private suspend fun clearAll() {
        // Od dzieci do rodziców - baza pilnuje kluczy obcych.
        dao.deleteSetLogs()
        dao.deleteRestIntervals()
        dao.deleteSessionExercises()
        dao.deleteSessions()
        dao.deleteScheduledSessions()
        dao.deleteRecurrences()
        dao.deletePlanDayExercises()
        dao.deletePlanDays()
        dao.deletePlans()
        dao.deleteExerciseMedia()
        dao.deleteExercises()
        dao.deleteBodyEntries()
        dao.deleteMetricTypes()
        dao.deleteSeedImports()
        dao.deleteAppMeta()
    }

    /**
     * Dzieli wiersze na te, które trzeba wstawić, i te, które już są w bazie.
     *
     * Robimy to **przed** wstawianiem (a nie po odrzuceniu przez bazę), bo część
     * tabel nie ma klucza, który baza mogłaby sprawdzić: `metric_types` nie ma
     * żadnego ograniczenia unikalności, a ćwiczenia i plany mają je tylko na
     * `external_id` - wpis użytkownika nie ma po czym się zduplikować, choć
     * użytkownikowi chodzi o ten sam wskaźnik albo to samo ćwiczenie. Dlatego
     * dopasowanie robimy sami i tylko w trybie „dopisz”; w trybie „zastąp”
     * kopia ma wrócić wiernie, więc niczego nie scalamy.
     *
     * Zwraca wiersze do wstawienia i mapę „stary identyfikator → istniejący”.
     */
    private suspend fun <T> splitExisting(
        rows: List<T>,
        mode: BackupImportMode,
        oldId: (T) -> Long,
        lookup: suspend (T) -> Long?,
    ): Pair<List<T>, Map<Long, Long>> {
        if (mode == BackupImportMode.REPLACE || rows.isEmpty()) return rows to emptyMap()

        val toInsert = mutableListOf<T>()
        val existing = mutableMapOf<Long, Long>()
        rows.forEach { row ->
            val found = lookup(row)
            if (found != null) {
                existing[oldId(row)] = found
            } else {
                toInsert += row
            }
        }
        return toInsert to existing
    }

    /**
     * Wstawia wiersze i zwraca mapę „stary identyfikator → nowy”.
     *
     * W trybie „zastąp” wiersz wchodzi ze swoim identyfikatorem, więc mapa jest
     * tożsamościowa - i właśnie dlatego powiązania nie wymagają przepisywania.
     *
     * Wiersz, którego baza nie przyjęła (`-1`), nie trafia do mapy, a jego dzieci
     * idą za nim. W trybie „dopisz” dotyczy to dni planu: pozycja w planie jest
     * zajęta, więc dzień (a z nim jego ćwiczenia) wypada.
     */
    private suspend fun <T> copyRows(
        rows: List<T>,
        mode: BackupImportMode,
        counters: Counters,
        oldId: (T) -> Long,
        withId: (T, Long) -> T,
        insert: suspend (List<T>) -> List<Long>,
    ): IdMap {
        if (rows.isEmpty()) return IdMap()

        val prepared = when (mode) {
            BackupImportMode.REPLACE -> rows
            BackupImportMode.APPEND -> rows.map { row -> withId(row, 0) }
        }

        val newIds = insert(prepared)
        val map = IdMap()
        rows.forEachIndexed { index, row ->
            val newId = newIds[index]
            if (newId == -1L) {
                counters.skipped++
            } else {
                map.put(oldId(row), newId)
                counters.inserted++
            }
        }
        return map
    }

    private class Counters {
        var inserted = 0
        var reused = 0
        var skipped = 0
    }

    /** Stare identyfikatory jednej tabeli i ich odpowiedniki w bazie. */
    private class IdMap {

        private val ids = mutableMapOf<Long, Long>()

        fun put(old: Long, new: Long) {
            ids[old] = new
        }

        /** Wiersze rozpoznane w bazie wchodzą do tej samej mapy co wstawione. */
        fun putAll(other: Map<Long, Long>) {
            ids.putAll(other)
        }

        fun get(old: Long): Long? = ids[old]

        fun get(old: Long?): Long? = old?.let { value -> ids[value] }
    }
}
