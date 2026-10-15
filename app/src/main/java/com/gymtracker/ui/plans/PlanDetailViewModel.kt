package com.gymtracker.ui.plans

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.domain.model.Plan
import com.gymtracker.domain.model.PlanDay
import com.gymtracker.domain.model.PlanDayDraft
import com.gymtracker.domain.repository.PlanRepository
import com.gymtracker.domain.repository.SessionRepository
import com.gymtracker.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Plan z dniami w kolejności ich `position`. */
data class PlanDetailUiState(
    val isLoading: Boolean = true,
    val plan: Plan? = null,
    val days: List<PlanDay> = emptyList(),
)

@HiltViewModel
class PlanDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val plans: PlanRepository,
    private val sessions: SessionRepository,
) : ViewModel() {

    private val planId: Long = checkNotNull(savedStateHandle[Routes.ARG_PLAN_ID]) {
        "brak identyfikatora planu w trasie"
    }

    val uiState: StateFlow<PlanDetailUiState> = plans.observePlanDetails(planId)
        .map { details ->
            PlanDetailUiState(
                isLoading = false,
                plan = details?.plan,
                days = details?.days.orEmpty(),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = PlanDetailUiState(),
        )

    fun addDay(name: String, dayOfWeek: Int?) {
        if (name.isBlank()) return
        viewModelScope.launch {
            plans.addDay(planId, PlanDayDraft(name = name, dayOfWeek = dayOfWeek))
        }
    }

    fun updatePlan(name: String, description: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            plans.updatePlan(
                planId = planId,
                name = name,
                description = description.takeIf { text -> text.isNotBlank() },
            )
        }
    }

    fun deletePlan(onDeleted: () -> Unit) {
        viewModelScope.launch {
            plans.deletePlan(planId)
            onDeleted()
        }
    }

    fun renameDay(dayId: Long, name: String, dayOfWeek: Int?) {
        if (name.isBlank()) return
        viewModelScope.launch { plans.updateDay(dayId, name, dayOfWeek) }
    }

    fun deleteDay(dayId: Long) {
        viewModelScope.launch { plans.deleteDay(dayId) }
    }

    /** Nowa kolejność dni po przeciągnięciu (albo z menu „wyżej/niżej”). */
    fun moveDay(fromIndex: Int, toIndex: Int) {
        val ordered = uiState.value.days.map { day -> day.id }.moveItem(fromIndex, toIndex)
        if (ordered.size != uiState.value.days.size) return
        viewModelScope.launch { plans.reorderDays(planId, ordered) }
    }

    /** Rozpoczyna trening z dnia i podaje id sesji, żeby przejść na ekran treningu. */
    fun startSession(dayId: Long, onStarted: (Long) -> Unit) {
        viewModelScope.launch {
            sessions.startFromPlanDay(dayId)?.let { sessionId -> onStarted(sessionId) }
        }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
