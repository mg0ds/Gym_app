package com.gymtracker.ui.plans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.R
import com.gymtracker.domain.model.Exercise
import com.gymtracker.ui.exercises.equipmentLabel
import com.gymtracker.ui.exercises.muscleGroupLabel

/**
 * Wybór ćwiczenia z katalogu do dnia planu. Ekran nie pokazuje szczegółów
 * (nie ma tu czasu na oglądanie filmów) - szukamy, filtrujemy, wybieramy.
 * Wybór od razu dopisuje ćwiczenie do dnia, więc wracamy do listy dnia.
 */
@Composable
fun ExercisePickerScreen(
    onBack: () -> Unit,
    viewModel: ExercisePickerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ExercisePickerContent(
        uiState = uiState,
        onBack = onBack,
        onQueryChange = viewModel::onQueryChange,
        onMuscleGroupSelected = viewModel::onMuscleGroupSelected,
        onFavouritesToggle = viewModel::onFavouritesToggle,
        onPick = { exercise -> viewModel.pick(exercise, onBack) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExercisePickerContent(
    uiState: ExercisePickerUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onMuscleGroupSelected: (String?) -> Unit,
    onFavouritesToggle: () -> Unit,
    onPick: (Exercise) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (uiState.isAlternative) {
                                R.string.plan_pick_alternative_title
                            } else {
                                R.string.plan_pick_exercise_title
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(text = stringResource(R.string.exercises_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.exercises_clear_search),
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )

            FilterRow(
                uiState = uiState,
                onMuscleGroupSelected = onMuscleGroupSelected,
                onFavouritesToggle = onFavouritesToggle,
            )

            when {
                uiState.isLoading -> Box(modifier = Modifier.fillMaxSize())

                uiState.exercises.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.exercises_no_results_title),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                }

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items = uiState.exercises, key = { exercise -> exercise.id }) { exercise ->
                        ExercisePickRow(exercise = exercise, onPick = onPick)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterRow(
    uiState: ExercisePickerUiState,
    onMuscleGroupSelected: (String?) -> Unit,
    onFavouritesToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = uiState.muscleGroup == null && !uiState.favouritesOnly,
            onClick = { onMuscleGroupSelected(null) },
            label = { Text(text = stringResource(R.string.exercises_filter_all)) },
        )
        FilterChip(
            selected = uiState.favouritesOnly,
            onClick = onFavouritesToggle,
            label = { Text(text = stringResource(R.string.exercises_filter_favourites)) },
            leadingIcon = {
                Icon(
                    imageVector = if (uiState.favouritesOnly) {
                        Icons.Filled.Favorite
                    } else {
                        Icons.Filled.FavoriteBorder
                    },
                    contentDescription = null,
                )
            },
        )
        uiState.muscleGroups.forEach { group ->
            FilterChip(
                selected = uiState.muscleGroup == group,
                onClick = { onMuscleGroupSelected(group) },
                label = { Text(text = muscleGroupLabel(group).orEmpty()) },
            )
        }
    }
}

@Composable
private fun ExercisePickRow(
    exercise: Exercise,
    onPick: (Exercise) -> Unit,
) {
    val subtitle = listOfNotNull(
        muscleGroupLabel(exercise.muscleGroup),
        equipmentLabel(exercise.equipment),
    ).joinToString(separator = " • ")

    ListItem(
        modifier = Modifier.clickable { onPick(exercise) },
        headlineContent = {
            Text(
                text = exercise.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
