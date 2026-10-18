package com.gymtracker.ui.body

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gymtracker.R
import com.gymtracker.domain.model.BuiltinMetric
import com.gymtracker.domain.model.MetricType

/**
 * Nazwy wbudowanych wskaźników.
 *
 * W bazie leży zapasowa nazwa (po angielsku), ale dopóki wskaźnik ma swój klucz,
 * pokazujemy nazwę z zasobów - dzięki temu polski i angielski interfejs widzą
 * swoje nazwy. Pierwsza zmiana nazwy przez użytkownika czyści klucz
 * (`BodyDao.updateType`) i od tej chwili wygrywa wpis z bazy.
 */
@Composable
fun MetricType.displayName(): String = builtin?.let { metric -> builtinLabel(metric) } ?: name

@Composable
private fun builtinLabel(metric: BuiltinMetric): String = stringResource(
    when (metric) {
        BuiltinMetric.WEIGHT -> R.string.metric_builtin_weight
        BuiltinMetric.CHEST -> R.string.metric_builtin_chest
        BuiltinMetric.BICEPS -> R.string.metric_builtin_biceps
        BuiltinMetric.WAIST -> R.string.metric_builtin_waist
        BuiltinMetric.THIGH -> R.string.metric_builtin_thigh
        BuiltinMetric.CALF -> R.string.metric_builtin_calf
        BuiltinMetric.BODY_FAT -> R.string.metric_builtin_body_fat
        BuiltinMetric.NECK -> R.string.metric_builtin_neck
        BuiltinMetric.SHOULDERS -> R.string.metric_builtin_shoulders
        BuiltinMetric.FOREARM -> R.string.metric_builtin_forearm
    },
)
