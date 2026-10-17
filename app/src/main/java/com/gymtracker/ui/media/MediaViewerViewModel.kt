package com.gymtracker.ui.media

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.domain.model.ExerciseMedia
import com.gymtracker.domain.repository.MediaRepository
import com.gymtracker.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Stan podglądu załącznika: sam załącznik i jego własna miniatura. */
data class MediaViewerUiState(
    val isLoading: Boolean = true,
    val media: ExerciseMedia? = null,
    /** Nasza miniatura - ratunek, gdy dostęp do oryginału wygasł. */
    val thumbnail: File? = null,
)

/**
 * Podgląd załącznika. Załącznik bierzemy ze strumienia ćwiczenia (a nie
 * z argumentu trasy), żeby po skasowaniu wrócił `null` i ekran sam to pokazał -
 * nie ma sensu trzymać w pamięci kopii, która może już nie istnieć.
 */
@HiltViewModel
class MediaViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    private val exerciseId: Long = checkNotNull(savedStateHandle[Routes.ARG_EXERCISE_ID])
    private val mediaId: Long = checkNotNull(savedStateHandle[Routes.ARG_MEDIA_ID])

    val uiState: StateFlow<MediaViewerUiState> = mediaRepository.observeMedia(exerciseId)
        .map { items -> items.firstOrNull { item -> item.id == mediaId } }
        .map { media ->
            MediaViewerUiState(
                isLoading = false,
                media = media,
                thumbnail = media?.let(mediaRepository::thumbnailFile),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = MediaViewerUiState(),
        )

    fun setCaption(caption: String) {
        viewModelScope.launch { mediaRepository.setCaption(mediaId, caption) }
    }

    /** Odpina załącznik; plik w galerii zostaje nietknięty. */
    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            mediaRepository.delete(mediaId)
            onDeleted()
        }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
