package com.gymtracker.ui.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.domain.model.PlanDay
import com.gymtracker.domain.model.PlannedWorkout
import com.gymtracker.domain.model.RecurrenceDraft
import com.gymtracker.domain.repository.CalendarRepository
import com.gymtracker.domain.repository.PlanRepository
import com.gymtracker.domain.repository.ScheduleSaveResult
import com.gymtracker.domain.schedule.PlannedDayName
import com.gymtracker.domain.schedule.RecurrenceExpander
import com.gymtracker.domain.schedule.ScheduleError
import com.gymtracker.domain.schedule.ScheduleLimits
import com.gymtracker.domain.time.TimeProvider
import com.gymtracker.domain.time.today
import com.gymtracker.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Dwa sposoby planowania: reguła powtarzania albo ręcznie wybrane dni. */
enum class ScheduleMode { RULE, DATES }

/** Formularz harmonogramu: co pokazać i co zapisać. */
data class ScheduleFormUiState(
    val today: LocalDate,
    val horizonEnd: LocalDate,
    val startDate: LocalDate,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val planName: String = "",
    val planDays: List<PlanDay> = emptyList(),
    val mode: ScheduleMode = ScheduleMode.RULE,
    /** Wybrany dzień planu; `null` = rotacja po wszystkich dniach. */
    val selectedDayId: Long? = null,
    val endDate: LocalDate? = null,
    val intervalText: String = "1",
    val weekdays: Set<DayOfWeek> = emptySet(),
    /** Dni tygodnia wynikające z harmonogramu planu - podpowiedź w formularzu. */
    val weekdaysFromPlan: Set<DayOfWeek> = emptySet(),
    val pickedDates: Set<LocalDate> = emptySet(),
    val month: YearMonth = YearMonth.from(startDate),
    val preview: List<PlannedWorkout> = emptyList(),
    val errors: List<ScheduleError> = emptyList(),
    val isEditing: Boolean = false,
) {
    val isRule: Boolean get() = mode == ScheduleMode.RULE

    val rotatesDays: Boolean get() = selectedDayId == null

    val intervalInvalid: Boolean get() = intervalText.toIntOrNull() == null

    val canSave: Boolean get() = !isLoading && !isSaving && errors.isEmpty() &&
        (isRule || pickedDates.isNotEmpty())
}

/**
 * Formularz harmonogramu planu.
 *
 * Podgląd przelicza się po każdej zmianie pola - widzimy dokładnie te daty,
 * które trafią do kalendarza. Zakres jest zawsze ograniczony do 20 tygodni
 * w przód ([ScheduleLimits]), a dni tygodnia podpowiadamy z harmonogramu planu
 * (dni z seeda mają przypisany dzień tygodnia).
 */
@HiltViewModel
class ScheduleFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val plans: PlanRepository,
    private val calendar: CalendarRepository,
    timeProvider: TimeProvider,
) : ViewModel() {

    private val planId: Long = checkNotNull(savedStateHandle[Routes.ARG_PLAN_ID]) {
        "brak identyfikatora planu w trasie"
    }
    private val recurrenceId: Long? = savedStateHandle.get<Long>(Routes.ARG_RECURRENCE_ID)
        ?.takeIf { id -> id != NO_ID }

    private val today: LocalDate = timeProvider.today()

    private val _uiState = MutableStateFlow(
        ScheduleFormUiState(
            today = today,
            horizonEnd = ScheduleLimits.horizonEnd(today),
            startDate = today,
        ),
    )
    val uiState: StateFlow<ScheduleFormUiState> = _uiState.asStateFlow()

    private var previewJob: Job? = null

    init {
        viewModelScope.launch {
            val details = plans.observePlanDetails(planId).first()
            val days = details?.days.orEmpty()
            val fromPlan = days.mapNotNull { day -> day.dayOfWeek }
                .map { dayOfWeek -> DayOfWeek.of(dayOfWeek + 1) }
                .toSet()
            val recurrence = recurrenceId?.let { id -> calendar.getRecurrence(id) }

            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    planName = details?.plan?.name.orEmpty(),
                    planDays = days,
                    selectedDayId = recurrence?.planDayId,
                    startDate = recurrence?.startDate ?: state.startDate,
                    endDate = recurrence?.endDate,
                    intervalText = (recurrence?.intervalDays ?: DEFAULT_INTERVAL).toString(),
                    weekdays = recurrence?.weekdays ?: fromPlan,
                    weekdaysFromPlan = fromPlan,
                    month = YearMonth.from(recurrence?.startDate ?: state.startDate),
                    isEditing = recurrence != null,
                )
            }
            refreshPreview()
        }
    }

    fun setMode(mode: ScheduleMode) = update { state -> state.copy(mode = mode) }

    /** [dayId] = `null` włącza rotację dni planu. */
    fun selectDay(dayId: Long?) = update { state -> state.copy(selectedDayId = dayId) }

    fun setStartDate(date: LocalDate) = update { state ->
        val clamped = date.coerceIn(state.today, state.horizonEnd)
        state.copy(startDate = clamped, month = YearMonth.from(clamped))
    }

    fun setEndDate(date: LocalDate?) = update { state ->
        state.copy(endDate = date?.coerceIn(state.startDate, state.horizonEnd))
    }

    fun clearEndDate() = update { state -> state.copy(endDate = null) }

    fun setInterval(text: String) = update { state ->
        state.copy(intervalText = text.filter { char -> char.isDigit() }.take(MAX_INTERVAL_DIGITS))
    }

    fun toggleWeekday(day: DayOfWeek) = update { state ->
        state.copy(
            weekdays = if (day in state.weekdays) state.weekdays - day else state.weekdays + day,
        )
    }

    fun clearWeekdays() = update { state -> state.copy(weekdays = emptySet()) }

    fun applyWeekdaysFromPlan() = update { state ->
        state.copy(weekdays = state.weekdaysFromPlan)
    }

    fun togglePickedDate(date: LocalDate) = update { state ->
        state.copy(
            pickedDates = if (date in state.pickedDates) {
                state.pickedDates - date
            } else {
                state.pickedDates + date
            },
        )
    }

    fun showPreviousMonth() = update { state -> state.copy(month = state.month.minusMonths(1)) }

    fun showNextMonth() = update { state ->
        val next = state.month.plusMonths(1)
        if (YearMonth.from(state.horizonEnd).isBefore(next)) state else state.copy(month = next)
    }

    /** Zapisuje harmonogram i woła [onSaved], gdy dane są poprawne. */
    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        if (!state.canSave) return
        viewModelScope.launch {
            _uiState.update { current -> current.copy(isSaving = true) }
            val saved = when (state.mode) {
                ScheduleMode.DATES -> savePickedDates(state)
                ScheduleMode.RULE -> saveRecurrence(state)
            }
            _uiState.update { current -> current.copy(isSaving = false) }
            if (saved) onSaved()
        }
    }

    private suspend fun savePickedDates(state: ScheduleFormUiState): Boolean {
        calendar.planDates(
            planId = planId,
            planDayId = state.selectedDayId,
            dates = state.pickedDates.toList(),
        )
        return true
    }

    private suspend fun saveRecurrence(state: ScheduleFormUiState): Boolean {
        val draft = state.toDraft(planId)
        val result = recurrenceId
            ?.let { id -> calendar.replaceRecurrence(id, draft) }
            ?: calendar.planRecurrence(draft)
        return when (result) {
            is ScheduleSaveResult.Saved -> true
            is ScheduleSaveResult.Invalid -> {
                _uiState.update { current -> current.copy(errors = result.preview.errors) }
                false
            }
        }
    }

    private fun update(transform: (ScheduleFormUiState) -> ScheduleFormUiState) {
        _uiState.update(transform)
        refreshPreview()
    }

    /**
     * Przelicza podgląd. Krótka zwłoka zbiera zmiany z animacji klawiatury
     * (pole „co ile dni”), a każde nowe wywołanie anuluje poprzednie.
     */
    private fun refreshPreview() {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            delay(PREVIEW_DEBOUNCE_MS)
            val state = _uiState.value
            val (errors, workouts) = when {
                state.planDays.isEmpty() -> listOf(ScheduleError.NO_PLAN_DAY) to emptyList()

                state.isRule -> {
                    val preview = calendar.previewRecurrence(state.toDraft(planId))
                    preview.errors to preview.workouts
                }

                else -> emptyList<ScheduleError>() to RecurrenceExpander.assignDays(
                    dates = state.pickedDates.sorted(),
                    days = state.rotationDays(),
                )
            }
            _uiState.update { current -> current.copy(errors = errors, preview = workouts) }
        }
    }

    private fun ScheduleFormUiState.toDraft(planId: Long) = RecurrenceDraft(
        planId = planId,
        planDayId = selectedDayId,
        startDate = startDate,
        endDate = endDate,
        intervalDays = intervalText.toIntOrNull() ?: 0,
        weekdays = weekdays,
    )

    /** Dni w kolejności rotacji; wybrany dzień zwija listę do jednego. */
    private fun ScheduleFormUiState.rotationDays(): List<PlannedDayName> {
        val all = planDays.map { day -> PlannedDayName(day.id, day.name) }
        val selected = selectedDayId ?: return all
        return all.filter { day -> day.id == selected }
    }

    private companion object {
        const val PREVIEW_DEBOUNCE_MS = 60L
        const val DEFAULT_INTERVAL = 1
        const val MAX_INTERVAL_DIGITS = 3
        const val NO_ID = 0L
    }
}
