package com.gymtracker.ui.body

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.domain.body.BodyProgress
import com.gymtracker.domain.body.BodyProgressCalculator
import com.gymtracker.domain.model.BodyEntry
import com.gymtracker.domain.model.MetricType
import com.gymtracker.domain.repository.BodyRepository
import com.gymtracker.domain.stats.StatsRange
import com.gymtracker.domain.time.TimeProvider
import com.gymtracker.domain.time.today
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BodyUiState(
    val isLoading: Boolean = true,
    /** Aktywne wskaźniki - kafelki przełącznika u góry ekranu. */
    val types: List<MetricType> = emptyList(),
    val selected: MetricType? = null,
    val range: StatsRange = StatsRange.MONTH_3,
    /** Pomiary wybranego wskaźnika z całej historii, od najnowszych. */
    val entries: List<BodyEntry> = emptyList(),
    /** Pomiary w wybranym zakresie - to, co pokazuje lista pod wykresem. */
    val entriesInRange: List<BodyEntry> = emptyList(),
    val progress: BodyProgress = BodyProgress(),
    /**
     * Dzisiejsza data. ViewModel zawsze wpisuje tu dzień z wstrzykniętego
     * zegara; wartość domyślna istnieje tylko dla stanu początkowego i podglądów.
     */
    val today: LocalDate = LocalDate.now(),
)

/**
 * Karta postaci: wskaźniki, pomiary i wykres.
 *
 * Dwie decyzje warte zapisania:
 * 1. Wybór wskaźnika trzymamy **w pamięci**, a nie w argumencie trasy: przełącznik
 *    ma działać natychmiast, a nie dokładać ekran do stosu. Wartość domyślna to
 *    pierwszy wskaźnik, więc ekran nie startuje w pustce.
 * 2. „Dziś” liczymy przy każdym przeliczeniu, a nie raz przy tworzeniu
 *    ViewModelu: aplikacja żyje dłużej niż jeden dzień, a zakres „3 miesiące”
 *    i „3 dni temu” muszą się przesunąć po północy.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BodyViewModel @Inject constructor(
    private val repository: BodyRepository,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val selectedTypeId = MutableStateFlow<Long?>(null)
    private val range = MutableStateFlow(StatsRange.MONTH_3)

    private val types = repository.observeTypes()

    /** Wybrany wskaźnik; gdy wybór jest nieaktualny (archiwizacja), wracamy do pierwszego. */
    private val selected = combine(types, selectedTypeId) { list, id ->
        list.firstOrNull { type -> type.id == id } ?: list.firstOrNull()
    }

    private val entries = selected.flatMapLatest { type ->
        if (type == null) flowOf(emptyList()) else repository.observeEntries(type.id)
    }

    val uiState: StateFlow<BodyUiState> = combine(
        types,
        selected,
        entries,
        range,
    ) { types, selected, entries, range ->
        val today = timeProvider.today()
        BodyUiState(
            isLoading = false,
            types = types,
            selected = selected,
            range = range,
            entries = entries,
            entriesInRange = entries.filter { entry -> range.contains(entry.date, today) },
            progress = BodyProgressCalculator.calculate(
                entries = entries,
                range = range,
                today = today,
            ),
            today = today,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = BodyUiState(),
    )

    fun onTypeSelected(typeId: Long) {
        selectedTypeId.value = typeId
    }

    fun onRangeSelected(value: StatsRange) {
        range.value = value
    }

    /** [entryId] == `null` to nowy pomiar, a podany identyfikator to jego poprawka. */
    fun saveEntry(entryId: Long?, date: LocalDate, value: Double, note: String?) {
        val typeId = uiState.value.selected?.id ?: return
        viewModelScope.launch {
            repository.saveEntry(
                metricTypeId = typeId,
                entryId = entryId,
                date = date,
                value = value,
                note = note,
            )
        }
    }

    fun deleteEntry(entryId: Long) {
        viewModelScope.launch { repository.deleteEntry(entryId) }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
