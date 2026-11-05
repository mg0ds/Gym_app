package com.gymtracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Etykieta informacyjna - np. autor planu, „wybór domyślny”, „superseria 1”.
 *
 * Świadomie **nie** używamy tu `AssistChip`/`SuggestionChip`: te komponenty są
 * przyciskami, więc czytnik ekranu zapowiada je jako „przycisk”, a stuknięcie
 * nic nie robi. Etykieta ma wyglądać jak etykieta i niczego nie udawać.
 */
@Composable
fun InfoTag(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
