package com.gymtracker.data.repo

import androidx.room.withTransaction
import com.gymtracker.data.db.AppDatabase
import com.gymtracker.data.db.daos.CalendarDao
import com.gymtracker.data.db.daos.PlanDao
import com.gymtracker.data.db.entities.ScheduledSessionEntity
import com.gymtracker.domain.model.PlannedWorkout
import com.gymtracker.domain.model.Recurrence
import com.gymtracker.domain.model.RecurrenceDraft
import com.gymtracker.domain.model.ScheduleStatus
import com.gymtracker.domain.model.ScheduledWorkout
import com.gymtracker.domain.repository.CalendarRepository
import com.gymtracker.domain.repository.ScheduleSaveResult
import com.gymtracker.domain.schedule.PlannedDayName
import com.gymtracker.domain.schedule.RecurrenceExpander
import com.gymtracker.domain.schedule.ScheduleError
import com.gymtracker.domain.schedule.ScheduleLimits
import com.gymtracker.domain.schedule.SchedulePreview
import com.gymtracker.domain.time.TimeProvider
import com.gymtracker.domain.time.today
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Kalendarz treningów.
 *
 * Reguła jest rozwijana do wpisów **raz**, przy planowaniu. To repozytorium
 * czyta potem tylko `scheduled_sessions` w zakresie dat - jedno zapytanie na
 * miesiąc. Treningi widoczne w kalendarzu (także te bez wpisu w planie) czyta
 * `SessionRepository.observeWorkoutsBetween`.
 *
 * Horyzont planowania to 20 tygodni w przód ([RecurrenceExpander]): tyle, ile
 * obejmuje sensowny mezocykl, i tyle, by lista nie rosła bez końca. Gdy dni
 * mijają, horyzont przesuwa się razem z „dziś” - [refreshHorizon] dosypuje
 * wpisy regułom, które już mają ślad w kalendarzu (reguła bez ani jednego wpisu
 * nie jest wskrzeszana: użytkownik mógł je posprzątać ręcznie).
 */
@Singleton
class CalendarRepositoryImpl @Inject constructor(
    private val calendarDao: CalendarDao,
    private val planDao: PlanDao,
    private val database: AppDatabase,
    private val timeProvider: TimeProvider,
) : CalendarRepository {

    override fun observeRange(from: LocalDate, to: LocalDate): Flow<List<ScheduledWorkout>> =
        calendarDao.observeBetween(from.toWire(), to.toWire())
            .map { rows -> rows.map { row -> row.toDomain() } }

    override fun observeRecurrencesOfPlan(planId: Long): Flow<List<Recurrence>> =
        calendarDao.observeRecurrencesOfPlan(planId)
            .map { rows -> rows.map { row -> row.toDomain() } }

    override suspend fun getRecurrence(recurrenceId: Long): Recurrence? =
        calendarDao.getRecurrence(recurrenceId)?.toDomain()

    override suspend fun planDates(planId: Long, planDayId: Long?, dates: List<LocalDate>): Int =
        database.withTransaction {
            val today = timeProvider.today()
            val horizon = ScheduleLimits.horizonEnd(today)
            val wanted = dates.distinct()
                .filter { date -> !date.isBefore(today) && !date.isAfter(horizon) }
                .sorted()
            if (wanted.isEmpty()) return@withTransaction 0
            val draft = RecurrenceDraft(
                planId = planId,
                planDayId = planDayId,
                startDate = wanted.first(),
            )
            val days = resolveDays(draft)
            if (days.isEmpty()) return@withTransaction 0
            insertWorkouts(
                recurrenceId = null,
                planId = planId,
                workouts = RecurrenceExpander.assignDays(wanted, days),
            )
        }

    override suspend fun previewRecurrence(draft: RecurrenceDraft): SchedulePreview {
        val prepared = prepare(draft)
        return SchedulePreview(
            errors = prepared.errors,
            workouts = if (prepared.errors.isEmpty()) prepared.workouts(draft) else emptyList(),
        )
    }

    override suspend fun planRecurrence(draft: RecurrenceDraft): ScheduleSaveResult =
        database.withTransaction {
            val prepared = prepare(draft)
            if (prepared.errors.isNotEmpty()) {
                return@withTransaction ScheduleSaveResult.Invalid(
                    SchedulePreview(prepared.errors, emptyList()),
                )
            }
            val entity = draft.toEntity(timeProvider.nowEpochMs())
            // Ten sam formularz zapisany drugi raz nie tworzy drugiej reguły -
            // przeliczamy istniejącą (wpisy `planned` wymieniamy, historia zostaje).
            val existing = calendarDao.findIdentical(
                planId = draft.planId,
                planDayId = entity.planDayId,
                startDate = entity.startDate,
                endDate = entity.endDate,
                intervalDays = entity.intervalDays,
                mask = entity.weekdaysMask,
            )
            val recurrenceId = if (existing == null) {
                calendarDao.insertRecurrence(entity)
            } else {
                calendarDao.deletePlannedOfRecurrence(existing.id)
                existing.id
            }
            ScheduleSaveResult.Saved(
                insertWorkouts(
                    recurrenceId = recurrenceId,
                    planId = draft.planId,
                    workouts = prepared.workouts(draft),
                ),
            )
        }

    override suspend fun replaceRecurrence(
        recurrenceId: Long,
        draft: RecurrenceDraft,
    ): ScheduleSaveResult = database.withTransaction {
        val prepared = prepare(draft)
        if (prepared.errors.isNotEmpty()) {
            return@withTransaction ScheduleSaveResult.Invalid(
                SchedulePreview(prepared.errors, emptyList()),
            )
        }
        // Stara reguła znika razem z zaplanowanymi wpisami; odbyte i pominięte
        // tracą tylko wskazanie na regułę (SET NULL) i zostają jako historia.
        calendarDao.deletePlannedOfRecurrence(recurrenceId)
        calendarDao.deleteRecurrence(recurrenceId)
        val newId = calendarDao.insertRecurrence(draft.toEntity(timeProvider.nowEpochMs()))
        ScheduleSaveResult.Saved(
            insertWorkouts(
                recurrenceId = newId,
                planId = draft.planId,
                workouts = prepared.workouts(draft),
            ),
        )
    }

    override suspend fun deleteRecurrence(recurrenceId: Long) = database.withTransaction {
        calendarDao.deletePlannedOfRecurrence(recurrenceId)
        calendarDao.deleteRecurrence(recurrenceId)
    }

    override suspend fun refreshHorizon(): Int = database.withTransaction {
        val today = timeProvider.today()
        var inserted = 0
        for (rule in calendarDao.getRecurrences()) {
            val planId = rule.planId ?: continue
            // Reguła bez wpisów nie jest dosypywana - coś ją już usunęło.
            val lastDate = calendarDao.lastDateOfRecurrence(rule.id) ?: continue
            val domain = rule.toDomain()
            val draft = domain.toDraft(planId)
            val days = resolveDays(draft)
            if (days.isEmpty()) continue
            val upcoming = RecurrenceExpander.dates(draft, today)
                .filter { date -> date.toWire() > lastDate }
            if (upcoming.isEmpty()) continue
            val workouts = RecurrenceExpander.assignDays(
                dates = upcoming,
                days = days,
                startIndex = calendarDao.countOfRecurrence(rule.id),
            )
            inserted += insertWorkouts(
                recurrenceId = rule.id,
                planId = planId,
                workouts = workouts,
            )
        }
        inserted
    }

    override suspend fun setStatus(id: Long, status: ScheduleStatus) {
        calendarDao.setStatus(id, status.wire)
    }

    override suspend fun move(id: Long, date: LocalDate) {
        calendarDao.setDate(id, date.toWire())
    }

    override suspend fun removeEntry(id: Long) {
        calendarDao.deleteScheduled(id)
    }

    private suspend fun prepare(draft: RecurrenceDraft): Prepared {
        val today = timeProvider.today()
        val days = resolveDays(draft)
        val errors = RecurrenceExpander.validate(draft, today, days)
        return Prepared(today, days, errors)
    }

    /** Dni do rotacji: wybrany dzień (lista jednoelementowa) albo wszystkie dni planu. */
    private suspend fun resolveDays(draft: RecurrenceDraft): List<PlannedDayName> {
        val fixedDayId = draft.planDayId
        if (fixedDayId != null) {
            val day = planDao.getDay(fixedDayId) ?: return emptyList()
            if (day.planId != draft.planId) return emptyList()
            return listOf(PlannedDayName(day.id, day.name))
        }
        return planDao.getDays(draft.planId).map { day -> PlannedDayName(day.id, day.name) }
    }

    private suspend fun insertWorkouts(
        recurrenceId: Long?,
        planId: Long,
        workouts: List<PlannedWorkout>,
    ): Int {
        if (workouts.isEmpty()) return 0
        val now = timeProvider.nowEpochMs()
        val rows = workouts.map { workout ->
            ScheduledSessionEntity(
                date = workout.date.toWire(),
                planId = planId,
                planDayId = workout.planDayId,
                recurrenceId = recurrenceId,
                title = workout.title,
                status = ScheduledSessionEntity.STATUS_PLANNED,
                createdAtEpochMs = now,
            )
        }
        return calendarDao.insertScheduled(rows).count { id -> id != IGNORED_ROW }
    }

    /** Materiał do zapisu reguły: „dziś”, dni rotacji i błędy walidacji. */
    private data class Prepared(
        val today: LocalDate,
        val days: List<PlannedDayName>,
        val errors: List<ScheduleError>,
    ) {
        fun workouts(draft: RecurrenceDraft): List<PlannedWorkout> = RecurrenceExpander.assignDays(
            dates = RecurrenceExpander.dates(draft, today),
            days = days,
        )
    }

    private companion object {
        /** `@Insert(IGNORE)` zwraca -1 dla wiersza pominiętego przez konflikt. */
        const val IGNORED_ROW = -1L
    }
}
