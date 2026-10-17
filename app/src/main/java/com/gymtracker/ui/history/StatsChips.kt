package com.gymtracker.ui.history

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gymtracker.R
import com.gymtracker.domain.stats.StatsRange

/**
 * Paski filtrów w historii i w statystykach.
 *
 * Wszystkie przewijają się w poziomie: lista ćwiczeń z historii bywa długa,
 * a chip, który nie mieści się na ekranie, nie może zostać nieosiągalny.
 */

/** Zakres wykresu: 1M / 3M / 6M / 1R / wszystko. */
@Composable
fun RangeChipsRow(
    range: StatsRange,
    onRangeSelected: (StatsRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChipsRow(modifier = modifier) {
        StatsRange.entries.forEach { value ->
            FilterChip(
                selected = value == range,
                onClick = { onRangeSelected(value) },
                label = { Text(stringResource(value.labelRes())) },
            )
        }
    }
}

/** Ćwiczenia obecne w historii - filtr „pokaż tylko treningi z tym ćwiczeniem”. */
@Composable
fun ExerciseChipsRow(
    exercises: List<HistoryExerciseOption>,
    selectedId: Long?,
    onExerciseSelected: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChipsRow(modifier = modifier) {
        FilterChip(
            selected = selectedId == null,
            onClick = { onExerciseSelected(null) },
            label = { Text(stringResource(R.string.history_filter_all_exercises)) },
        )
        exercises.forEach { option ->
            FilterChip(
                selected = option.id == selectedId,
                onClick = { onExerciseSelected(option.id) },
                label = { Text(option.name) },
            )
        }
    }
}

/** Nazwy treningów obecne w historii - filtr „który dzień planu”. */
@Composable
fun NameChipsRow(
    names: List<String>,
    selectedName: String?,
    onNameSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChipsRow(modifier = modifier) {
        FilterChip(
            selected = selectedName == null,
            onClick = { onNameSelected(null) },
            label = { Text(stringResource(R.string.history_filter_all_workouts)) },
        )
        names.forEach { name ->
            FilterChip(
                selected = name == selectedName,
                onClick = { onNameSelected(name) },
                label = { Text(name) },
            )
        }
    }
}

@Composable
internal fun FilterChipsRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}
