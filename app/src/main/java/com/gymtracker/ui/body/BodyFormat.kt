package com.gymtracker.ui.body

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import com.gymtracker.R
import com.gymtracker.domain.body.BodyChange
import com.gymtracker.domain.body.BodyPoint
import com.gymtracker.domain.model.MetricDirection
import com.gymtracker.domain.model.MetricType
import com.gymtracker.ui.calendar.isoText
import com.gymtracker.ui.components.toPlainStringOrNull
import kotlin.math.roundToLong

/**
 * Teksty karty postaci.
 *
 * Liczby zaokrąglamy **tutaj**, a nie w bazie: pomiar wpisany jako `81.2`
 * i drugi jako `82.5` dają w `Double` różnicę `1.3000000000000007` - na ekranie
 * ma być `1.3`. Baza trzyma to, co zmierzył użytkownik, a ekran pokazuje dwie
 * cyfry po przecinku i obcina zera.
 */

/** „82.5 kg”; bez jednostki samo „82.5” (np. „przysiady: 12”). */
@Composable
fun metricValue(value: Double, unit: String?): String {
    val number = formatNumber(value)
    return if (unit.isNullOrBlank()) number else stringResource(R.string.metric_value_with_unit, number, unit)
}

/** „+1.3 kg” / „-2 kg” - ze znakiem, bo kierunek zmiany jest tu informacją. */
@Composable
fun metricDelta(change: BodyChange, unit: String?): String {
    val sign = if (change.value > 0.0) "+" else ""
    return sign + metricValue(change.value, unit)
}

/** „82.5 kg • 17.10.2026” - wartość z dniem, w którym ją zmierzono. */
@Composable
fun metricValueWithDate(point: BodyPoint, unit: String?): String = stringResource(
    R.string.metric_value_with_date,
    metricValue(point.value, unit),
    point.date.isoText(),
)

/** „+1.3 kg • 5 dni” - zmiana razem z odległością między pomiarami. */
@Composable
fun metricChangeText(change: BodyChange, unit: String?): String = stringResource(
    R.string.metric_change_with_span,
    metricDelta(change, unit),
    daysSpan(change.days),
)

/** „5 dni”, „1 dzień” - odległość między dwoma pomiarami. */
@Composable
fun daysSpan(days: Long): String =
    pluralStringResource(R.plurals.metric_days_span, days.toInt(), days)

/** „dziś” / „wczoraj” / „3 dni temu”. */
@Composable
fun daysAgo(days: Long): String = when (days) {
    0L -> stringResource(R.string.metric_days_today)
    1L -> stringResource(R.string.metric_days_yesterday)
    else -> pluralStringResource(R.plurals.metric_days_ago, days.toInt(), days)
}

/** „12 dni z rzędu”. */
@Composable
fun streakText(days: Int): String =
    pluralStringResource(R.plurals.metric_streak_days, days, days)

/**
 * Cel wskaźnika razem z tym, ile zostało: „78 kg • zostało 4.5 kg”.
 *
 * „Zostało” liczymy tylko wtedy, gdy znamy kierunek (`neutral` nie mówi, czy
 * bliżej celu znaczy lepiej), a przy osiągniętym celu pokazujemy to wprost.
 */
@Composable
fun metricTargetText(type: MetricType, latest: Double?): String? {
    val target = type.target ?: return null
    val targetText = metricValue(target, type.unit)
    val value = latest ?: return targetText

    val remaining = when (type.direction) {
        MetricDirection.UP_GOOD -> target - value
        MetricDirection.DOWN_GOOD -> value - target
        MetricDirection.NEUTRAL -> null
    }

    return when {
        remaining == null -> targetText
        remaining <= 0.0 -> stringResource(R.string.metric_target_reached, targetText)
        else -> stringResource(
            R.string.metric_target_remaining,
            targetText,
            metricValue(remaining, type.unit),
        )
    }
}

/**
 * Dwie cyfry po przecinku i bez zer końcowych: `1.3000000000000007` → `1.3`,
 * `82.50` → `82.5`, `82.00` → `82`.
 */
internal fun formatNumber(value: Double): String {
    val rounded = (value * 100.0).roundToLong() / 100.0
    return rounded.toPlainStringOrNull() ?: "0"
}

/** Etykieta kierunku zmian - ten sam wybór pokazuje formularz wskaźnika. */
@StringRes
internal fun MetricDirection.labelRes(): Int = when (this) {
    MetricDirection.UP_GOOD -> R.string.metric_direction_up
    MetricDirection.DOWN_GOOD -> R.string.metric_direction_down
    MetricDirection.NEUTRAL -> R.string.metric_direction_neutral
}
