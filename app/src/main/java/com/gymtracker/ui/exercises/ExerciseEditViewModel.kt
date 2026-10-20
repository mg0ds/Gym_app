package com.gymtracker.ui.exercises

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.domain.model.ExerciseDraft
import com.gymtracker.domain.model.ExerciseTracking
import com.gymtracker.domain.model.InstructionSteps
import com.gymtracker.domain.model.YouTubeLinks
import com.gymtracker.domain.repository.ExerciseRepository
import com.gymtracker.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Stan formularza ćwiczenia. Pola liczbowe trzymamy jako tekst (tak je wpisuje
 * użytkownik), a na liczby zamieniamy dopiero przy zapisie - dzięki temu puste
 * pole i „0” to dwie różne rzeczy.
 */
data class ExerciseEditUiState(
    val isNew: Boolean = true,
    val isLoading: Boolean = false,
    val name: String = "",
    val isNameInvalid: Boolean = false,
    val muscleGroup: String? = null,
    val equipment: String? = null,
    /** Co zapisujemy w serii; domyślnie wynika ze sprzętu, ale użytkownik może zmienić. */
    val tracking: ExerciseTracking = ExerciseTracking.WEIGHT_REPS,
    /** Czy typ został wybrany ręcznie (wtedy zmiana sprzętu go nie nadpisuje). */
    val trackingTouched: Boolean = false,
    val mechanics: String? = null,
    val level: String? = null,
    val secondaryMuscles: List<String> = emptyList(),
    val youtubeUrl: String = "",
    /** Kroki wykonania jako tekst: jedna linia = jeden krok. */
    val instructions: String = "",
    val notes: String = "",
    val sets: String = "",
    val repsMin: String = "",
    val repsMax: String = "",
    val restSeconds: String = "",
) {
    /** Podpowiedź pod polem linku: rozpoznaliśmy film czy zapiszemy zwykły link. */
    val youtubeLinkRecognized: Boolean get() = YouTubeLinks.idOf(youtubeUrl) != null
}

@HiltViewModel
class ExerciseEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ExerciseRepository,
) : ViewModel() {

    /** `null` = dodajemy nowe ćwiczenie. */
    private val exerciseId: Long? = savedStateHandle.get<Long>(Routes.ARG_EXERCISE_ID)

    private val _uiState = MutableStateFlow(
        ExerciseEditUiState(
            isNew = exerciseId == null,
            isLoading = exerciseId != null,
        ),
    )
    val uiState: StateFlow<ExerciseEditUiState> = _uiState.asStateFlow()

    init {
        val id = exerciseId
        if (id != null) {
            viewModelScope.launch {
                val exercise = repository.getExercise(id)
                _uiState.update { state ->
                    if (exercise == null) {
                        state.copy(isLoading = false)
                    } else {
                        state.copy(
                            isLoading = false,
                            name = exercise.name,
                            muscleGroup = exercise.muscleGroup,
                            equipment = exercise.equipment,
                            tracking = exercise.tracking,
                            trackingTouched = true,
                            mechanics = exercise.mechanics,
                            level = exercise.level,
                            secondaryMuscles = exercise.secondaryMuscles,
                            youtubeUrl = exercise.youtubeUrl.orEmpty(),
                            instructions = InstructionSteps.toText(exercise.instructions),
                            notes = exercise.notes.orEmpty(),
                            sets = exercise.defaultSets?.toString().orEmpty(),
                            repsMin = exercise.defaultRepsMin?.toString().orEmpty(),
                            repsMax = exercise.defaultRepsMax?.toString().orEmpty(),
                            restSeconds = exercise.defaultRestS?.toString().orEmpty(),
                        )
                    }
                }
            }
        }
    }

    fun onNameChange(value: String) {
        _uiState.update { it.copy(name = value, isNameInvalid = false) }
    }

    fun onMuscleGroupChange(value: String?) {
        _uiState.update { it.copy(muscleGroup = value) }
    }

    fun onEquipmentChange(value: String?) {
        _uiState.update { state ->
            state.copy(
                equipment = value,
                tracking = if (state.trackingTouched) {
                    state.tracking
                } else {
                    ExerciseTracking.defaultFor(value)
                },
            )
        }
    }

    fun onTrackingChange(value: ExerciseTracking) {
        _uiState.update { it.copy(tracking = value, trackingTouched = true) }
    }

    fun onMechanicsChange(value: String?) {
        _uiState.update { it.copy(mechanics = value) }
    }

    fun onLevelChange(value: String?) {
        _uiState.update { it.copy(level = value) }
    }

    fun onSecondaryMuscleToggle(value: String) {
        _uiState.update { state ->
            val muscles = if (value in state.secondaryMuscles) {
                state.secondaryMuscles - value
            } else {
                state.secondaryMuscles + value
            }
            state.copy(secondaryMuscles = muscles)
        }
    }

    fun onYoutubeUrlChange(value: String) {
        _uiState.update { it.copy(youtubeUrl = value) }
    }

    /** Kroki trzymamy jako tekst i normalizujemy dopiero przy zapisie. */
    fun onInstructionsChange(value: String) {
        _uiState.update { it.copy(instructions = value) }
    }

    fun onNotesChange(value: String) {
        _uiState.update { it.copy(notes = value) }
    }

    fun onSetsChange(value: String) {
        _uiState.update { it.copy(sets = value.digitsOnly()) }
    }

    fun onRepsMinChange(value: String) {
        _uiState.update { it.copy(repsMin = value.digitsOnly()) }
    }

    fun onRepsMaxChange(value: String) {
        _uiState.update { it.copy(repsMax = value.digitsOnly()) }
    }

    fun onRestSecondsChange(value: String) {
        _uiState.update { it.copy(restSeconds = value.digitsOnly()) }
    }

    /** Zapisuje i oddaje id zapisanego ćwiczenia. Pusta nazwa zatrzymuje zapis. */
    fun save(onSaved: (Long) -> Unit) {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(isNameInvalid = true) }
            return
        }

        val draft = state.toDraft()
        viewModelScope.launch {
            val savedId = exerciseId ?: repository.addExercise(draft)
            if (exerciseId != null) {
                repository.updateExercise(exerciseId, draft)
            }
            onSaved(savedId)
        }
    }

    private fun ExerciseEditUiState.toDraft(): ExerciseDraft = ExerciseDraft(
        name = name,
        muscleGroup = muscleGroup,
        equipment = equipment,
        tracking = tracking,
        mechanics = mechanics,
        level = level,
        secondaryMuscles = secondaryMuscles,
        youtubeUrl = youtubeUrl,
        instructions = InstructionSteps.fromText(instructions),
        defaultSets = sets.toIntOrNull(),
        defaultRepsMin = repsMin.toIntOrNull(),
        defaultRepsMax = repsMax.toIntOrNull(),
        defaultRestS = restSeconds.toIntOrNull(),
        notes = notes,
    )

    private fun String.digitsOnly(): String = filter { it.isDigit() }.take(MAX_DIGITS)

    private companion object {
        const val MAX_DIGITS = 4
    }
}
