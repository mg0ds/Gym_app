package com.gymtracker.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.domain.model.Plan
import com.gymtracker.domain.model.PlanDraft
import com.gymtracker.domain.repository.PlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Lista planów: gotowe dane z bazy plus flaga pierwszego wczytania. */
data class PlanListUiState(
    val plans: List<Plan> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class PlanListViewModel @Inject constructor(
    private val plans: PlanRepository,
) : ViewModel() {

    val uiState: StateFlow<PlanListUiState> = plans.observePlans()
        .map { list -> PlanListUiState(plans = list, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = PlanListUiState(),
        )

    /** Tworzy plan od zera i oddaje jego id, żeby przejść od razu do edycji dni. */
    fun createPlan(name: String, description: String, onCreated: (Long) -> Unit) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return

        viewModelScope.launch {
            val planId = plans.createPlan(
                PlanDraft(
                    name = trimmedName,
                    description = description.trim().takeIf { text -> text.isNotEmpty() },
                ),
            )
            onCreated(planId)
        }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
