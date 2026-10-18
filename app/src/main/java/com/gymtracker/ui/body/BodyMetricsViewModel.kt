package com.gymtracker.ui.body

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.domain.model.MetricDirection
import com.gymtracker.domain.model.MetricType
import com.gymtracker.domain.repository.BodyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BodyMetricsUiState(
    val isLoading: Boolean = true,
    /** Wszystkie wskaźniki, także zarchiwizowane. */
    val types: List<MetricType> = emptyList(),
)

/**
 * Ekran „Wskaźniki”: dodawanie, poprawianie i archiwizacja.
 *
 * Archiwizacja jest tu, a nie na ekranie z wykresem, bo to operacja porządkowa,
 * a nie codzienna: pomiarów nie da się dodać do zarchiwizowanego wskaźnika, więc
 * przypadkowe schowanie wskaźnika zabrałoby też pomiar z zasięgu ręki.
 */
@HiltViewModel
class BodyMetricsViewModel @Inject constructor(
    private val repository: BodyRepository,
) : ViewModel() {

    val uiState: StateFlow<BodyMetricsUiState> = repository
        .observeTypes(includeArchived = true)
        .map { types -> BodyMetricsUiState(isLoading = false, types = types) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = BodyMetricsUiState(),
        )

    fun addType(name: String, unit: String?, direction: MetricDirection, target: Double?) {
        viewModelScope.launch { repository.addType(name, unit, direction, target) }
    }

    fun updateType(
        typeId: Long,
        name: String?,
        unit: String?,
        direction: MetricDirection,
        target: Double?,
    ) {
        viewModelScope.launch { repository.updateType(typeId, name, unit, direction, target) }
    }

    fun setArchived(typeId: Long, archived: Boolean) {
        viewModelScope.launch { repository.setArchived(typeId, archived) }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
