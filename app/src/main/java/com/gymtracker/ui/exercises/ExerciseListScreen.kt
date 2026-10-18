package com.gymtracker.ui.exercises

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/**
 * Katalog ćwiczeń: szukanie (z polskimi znakami), filtr po partii mięśniowej,
 * ulubione i przejście do szczegółów. Dodawanie własnych ćwiczeń przez „+”.
 */
@Composable
fun ExerciseListScreen(
    onExerciseClick: (Long) -> Unit,
    onAddExercise: () -> Unit,
    onOpenPlans: () -> Unit,
    onOpenArchive: () -> Unit,
    viewModel: ExerciseListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ExerciseListContent(
        uiState = uiState,
        onQueryChange = viewModel::onQueryChange,
        onMuscleGroupSelected = viewModel::onMuscleGroupSelected,
        onFavouritesToggle = viewModel::onFavouritesToggle,
        onFavouriteToggle = viewModel::toggleFavourite,
        onExerciseClick = onExerciseClick,
        onAddExercise = onAddExercise,
        onOpenPlans = onOpenPlans,
        onOpenArchive = onOpenArchive,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseListContent(
    uiState: ExerciseListUiState,
    onQueryChange: (String) -> Unit,
    onMuscleGroupSelected: (String?) -> Unit,
    onFavouritesToggle: () -> Unit,
    onFavouriteToggle: (Exercise) -> Unit,
    onExerciseClick: (Long) -> Unit,
    onAddExercise: () -> Unit,
    onOpenPlans: () -> Unit,
    onOpenArchive: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.exercises_title)) },
                actions = {
                    IconButton(onClick = onOpenPlans) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = stringResource(R.string.plans_title),
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.exercises_more_actions),
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            // Archiwum pokazujemy zawsze: gdy jest puste, sam
                            // wiersz jest odpowiedzią na „co się dzieje z
                            // usuniętym ćwiczeniem?”.
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (uiState.archivedCount > 0) {
                                            stringResource(
                                                R.string.exercises_archive_open,
                                                uiState.archivedCount,
                                            )
                                        } else {
                                            stringResource(R.string.archive_title)
                                        },
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    onOpenArchive()
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddExercise) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.exercises_add),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            SearchField(
                query = uiState.query,
                onQueryChange = onQueryChange,
            )

            FiltersRow(
                uiState = uiState,
                onMuscleGroupSelected = onMuscleGroupSelected,
                onFavouritesToggle = onFavouritesToggle,
            )

            when {
                uiState.isLoading -> Unit

                uiState.exercises.isEmpty() -> EmptyState(
                    query = uiState.query,
                    hasFilters = uiState.muscleGroup != null || uiState.favouritesOnly,
                )

                else -> LazyColumn(
                    // Miejsce na przycisk „+”, żeby nie zasłaniał ostatniego wiersza.
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    items(items = uiState.exercises, key = { exercise -> exercise.id }) { exercise ->
                        ExerciseRow(
                            exercise = exercise,
                            onFavouriteToggle = onFavouriteToggle,
                            onClick = onExerciseClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text(stringResource(R.string.exercises_search_hint)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
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
}

@Composable
private fun FiltersRow(
    uiState: ExerciseListUiState,
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
            onClick = {
                onMuscleGroupSelected(null)
                if (uiState.favouritesOnly) onFavouritesToggle()
            },
            label = { Text(stringResource(R.string.exercises_filter_all)) },
        )
        FilterChip(
            selected = uiState.favouritesOnly,
            onClick = onFavouritesToggle,
            label = { Text(stringResource(R.string.exercises_filter_favourites)) },
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
                label = { Text(muscleGroupLabel(group).orEmpty()) },
            )
        }
    }
}

@Composable
private fun ExerciseRow(
    exercise: Exercise,
    onFavouriteToggle: (Exercise) -> Unit,
    onClick: (Long) -> Unit,
) {
    val subtitle = listOfNotNull(
        muscleGroupLabel(exercise.muscleGroup),
        equipmentLabel(exercise.equipment),
    ).joinToString(separator = " • ")

    ListItem(
        modifier = Modifier.clickable { onClick(exercise.id) },
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
        trailingContent = {
            IconButton(onClick = { onFavouriteToggle(exercise) }) {
                Icon(
                    imageVector = if (exercise.isFavourite) {
                        Icons.Filled.Favorite
                    } else {
                        Icons.Filled.FavoriteBorder
                    },
                    contentDescription = stringResource(
                        if (exercise.isFavourite) {
                            R.string.exercise_favourite_remove
                        } else {
                            R.string.exercise_favourite_add
                        },
                    ),
                    tint = if (exercise.isFavourite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
    )
}

@Composable
private fun EmptyState(
    query: String,
    hasFilters: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val searching = query.isNotBlank() || hasFilters
            Text(
                text = stringResource(
                    if (searching) R.string.exercises_no_results_title else R.string.exercises_empty_title,
                ),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (searching) {
                    stringResource(R.string.exercises_no_results_body, query)
                } else {
                    stringResource(R.string.exercises_empty_body)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
