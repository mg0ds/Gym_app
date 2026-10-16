package com.gymtracker.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.domain.model.ScheduleStatus
import com.gymtracker.domain.model.ScheduledWorkout
import com.gymtracker.domain.model.SessionDetails
import com.gymtracker.domain.repository.CalendarRepository
import com.gymtracker.domain.repository.SessionRepository
import com.gymtracker.domain.schedule.ScheduleLimits
import com.gymtracker.domain.time.TimeProvider
import com.gymtracker.domain.time.today
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Stan ekranu kalendarza: miesiąc, wybrany dzień, wpisy i trwający trening. */
data class CalendarUiState(
    val isLoading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val today: LocalDate = LocalDate.now(),
    val horizonEnd: LocalDate = LocalDate.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val cells: List<CalendarCell> = emptyList(),
    val entriesByDate: Map<LocalDate, List<ScheduledWorkout>> = emptyMap(),
    val activeSession: SessionDetails? = null,
) {
    val selectedEntries: List<ScheduledWorkout> get() = entriesByDate[selectedDate].orEmpty()

    /** Dalej niż horyzont (20 tygodni) nie ma czego planować, więc nie przewijamy. */
    val canGoForward: Boolean get() = month.isBefore(YearMonth.from(horizonEnd))
}

/**
 * Kalendarz treningów.
 *
 * Widok czyta wpisy tylko z pokazywanego miesiąca (jedno zapytanie na miesiąc)
 * i pilnuje horyzontu planowania: w przód wolno przewinąć najwyżej do miesiąca
 * z ostatnim dozwolonym dniem.
 */
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val calendar: CalendarRepository,
    private val sessions: SessionRepository,
    timeProvider: TimeProvider,
) : ViewModel() {

    private val today: LocalDate = timeProvider.today()
    private val horizonEnd: LocalDate = ScheduleLimits.horizonEnd(today)

    private val month = MutableStateFlow(YearMonth.from(today))
    private val selectedDate = MutableStateFlow(today)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val monthEntries = month.flatMapLatest { yearMonth ->
        calendar.observeRange(yearMonth.atDay(1), yearMonth.atEndOfMonth())
    }

    val uiState: StateFlow<CalendarUiState> = combine(
        monthEntries,
        month,
        selectedDate,
        sessions.observeActiveSession(),
    ) { entries, yearMonth, selected, activeSession ->
        CalendarUiState(
            isLoading = false,
            month = yearMonth,
            today = today,
            horizonEnd = horizonEnd,
            selectedDate = selected,
            cells = monthGrid(yearMonth),
            entriesByDate = entries.groupBy { entry -> entry.date },
            activeSession = activeSession,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = CalendarUiState(
            month = YearMonth.from(today),
            today = today,
            horizonEnd = horizonEnd,
            selectedDate = today,
            cells = monthGrid(YearMonth.from(today)),
        ),
    )

    init {
        // Horyzont przesuwa się razem z „dziś” - przy wejściu dosypujemy regułom
        // wpisy, które wyszły z 20-tygodniowego okna.
        viewModelScope.launch { calendar.refreshHorizon() }
    }

    fun showPreviousMonth() {
        month.value = month.value.minusMonths(1)
    }

    fun showNextMonth() {
        if (!uiState.value.canGoForward) return
        month.value = month.value.plusMonths(1)
    }

    fun showToday() {
        month.value = YearMonth.from(today)
        selectedDate.value = today
    }

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }

    /** Rozpoczyna trening z wpisu i podaje id sesji, żeby przejść na jej ekran. */
    fun startWorkout(entryId: Long, onStarted: (Long) -> Unit) {
        viewModelScope.launch {
            sessions.startFromSchedule(entryId)?.let { sessionId -> onStarted(sessionId) }
        }
    }

    fun markStatus(entryId: Long, status: ScheduleStatus) {
        viewModelScope.launch { calendar.setStatus(entryId, status) }
    }

    /** Przeniesienie wpisu - data przycięta do okna planowania. */
    fun moveEntry(entryId: Long, date: LocalDate) {
        val target = date.coerceIn(today, horizonEnd)
        viewModelScope.launch { calendar.move(entryId, target) }
    }

    fun removeEntry(entryId: Long) {
        viewModelScope.launch { calendar.removeEntry(entryId) }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
