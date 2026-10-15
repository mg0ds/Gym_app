package com.gymtracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.domain.model.SessionDetails
import com.gymtracker.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Ekran „Dziś”. W M2 pilnuje przede wszystkim jednego: trwającego treningu.
 * Gdy taki jest, pokazujemy „Kontynuuj trening” - bez tego użytkownik mógłby
 * zgubić rozpoczęty trening po wyjściu z ekranu sesji.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    sessions: SessionRepository,
) : ViewModel() {

    val activeSession: StateFlow<SessionDetails?> = sessions.observeActiveSession().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = null,
    )

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
