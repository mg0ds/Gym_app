package com.gymtracker.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gymtracker.R
import com.gymtracker.domain.stats.ExerciseStatsPoint
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Wykresy postępu (Vico).
 *
 * Dwie decyzje, które łatwo pomylić z usterką:
 *
 * 1. **Oś X to kolejne treningi, nie kalendarz.** Na wykresie postępu pytanie
 *    brzmi „czy rosnę z treningu na trening”; przy rzadkich treningach
 *    proporcjonalna oś ścisnęłaby punkty w kupkę na jednym końcu. Datę każdego
 *    punktu widać pod wykresem, w tabeli wykonań.
 * 2. **Punkt bez ciężaru wypada z wykresu ciężaru**, a nie jest rysowany jako
 *    zero: zero znaczyłoby „podniosłem nic”, a to nieprawda.
 *
 * Oba wykresy mają włączony zoom i przewijanie - `CartesianChartHost` domyślnie
 * podłącza `rememberDefaultVicoZoomState`, więc nie trzeba tego nigdzie ustawiać.
 */

/** Wysokość wykresu - tyle, by osie i etykiety były czytelne, a ekran nie tonął w jednym wykresie. */
private val CHART_HEIGHT = 180.dp

/** „12.10” na osi X; pełne daty są w tabeli pod wykresami. */
private val AXIS_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d.MM")

/**
 * Najcięższa seria dnia, szacowany 1RM i - przy ćwiczeniach ramped - najlżejsza
 * seria dnia.
 *
 * Każdy punkt ma **widoczny znacznik**. Bez niego trening z jednym dniem danych
 * nie rysował żadnej linii (jeden punkt to nie odcinek), więc wykres wyglądał
 * na pusty, mimo że dzień był na osi.
 *
 * Trzecia linia pojawia się tylko wtedy, gdy w zakresie jest rampa
 * (najlżejsza < najcięższa). Przy stałym obciążeniu pokrywałaby się z pierwszą,
 * więc tylko szumiałaby legendę.
 */
@Composable
fun WeightProgressChart(
    points: List<ExerciseStatsPoint>,
    modifier: Modifier = Modifier,
) {
    val heaviestColor = MaterialTheme.colorScheme.primary
    val oneRepMaxColor = MaterialTheme.colorScheme.tertiary
    val lightestColor = MaterialTheme.colorScheme.secondary
    val weightPoints = points.filter { point -> point.topSetKg != null }
    val hasRamp = hasWeightRange(weightPoints)
    val producer = remember { CartesianChartModelProducer() }

    LaunchedEffect(weightPoints, hasRamp) {
        val x = weightPoints.indices.map { index -> index as Number }
        producer.runTransaction {
            lineModel {
                series(x, weightPoints.map { point -> (point.topSetKg ?: 0.0) as Number })
                series(x, weightPoints.map { point -> (point.estimatedOneRepMaxKg ?: 0.0) as Number })
                if (hasRamp) {
                    series(
                        x,
                        weightPoints.map { point ->
                            (point.minSetKg ?: point.topSetKg ?: 0.0) as Number
                        },
                    )
                }
            }
        }
    }

    val heaviestMarker = marker(heaviestColor)
    val oneRepMaxMarker = marker(oneRepMaxColor)
    val lightestMarker = marker(lightestColor)
    val lines = buildList {
        add(
            LineCartesianLayer.Line(
                fill = LineCartesianLayer.LineFill.single(Fill(heaviestColor)),
                pointProvider = LineCartesianLayer.PointProvider.single(heaviestMarker),
            ),
        )
        add(
            LineCartesianLayer.Line(
                fill = LineCartesianLayer.LineFill.single(Fill(oneRepMaxColor)),
                pointProvider = LineCartesianLayer.PointProvider.single(oneRepMaxMarker),
            ),
        )
        if (hasRamp) {
            add(
                LineCartesianLayer.Line(
                    fill = LineCartesianLayer.LineFill.single(Fill(lightestColor)),
                    pointProvider = LineCartesianLayer.PointProvider.single(lightestMarker),
                ),
            )
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(lines),
                rangeProvider = chartRange(weightPoints),
            ),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = DateAxisFormatter(weightPoints)),
        ),
        modelProducer = producer,
        modifier = modifier.fillMaxWidth().height(CHART_HEIGHT),
    )
}

/** Znacznik punktu - bez niego pojedynczy trening jest niewidoczny. */
@Composable
private fun marker(color: Color): LineCartesianLayer.Point = LineCartesianLayer.Point(
    component = rememberShapeComponent(fill = Fill(color), shape = CircleShape),
    size = 6.dp,
)

/**
 * Zakres osi Y. Przy jednej wartości (jeden trening albo same równe ciężary)
 * auto-zakres miałby zerową wysokość - dodajemy margines, żeby punkt i linia
 * miały gdzie się narysować.
 */
private fun chartRange(points: List<ExerciseStatsPoint>): CartesianLayerRangeProvider {
    val values = points.flatMap { point ->
        listOfNotNull(point.topSetKg, point.minSetKg, point.estimatedOneRepMaxKg)
    }
    if (values.distinct().size > 1) return CartesianLayerRangeProvider.auto()
    val value = values.firstOrNull() ?: 0.0
    val padding = maxOf(MIN_RANGE_PADDING_KG, value * RANGE_PADDING_FRACTION)
    return CartesianLayerRangeProvider.fixed(
        minY = (value - padding).coerceAtLeast(0.0),
        maxY = value + padding,
    )
}

/** Czy w zakresie jest rampa: najlżejsza seria lżejsza od najcięższej. */
fun hasWeightRange(points: List<ExerciseStatsPoint>): Boolean = points.any { point ->
    val lightest = point.minSetKg
    val heaviest = point.topSetKg
    lightest != null && heaviest != null && lightest < heaviest
}

/** Objętość dnia: Σ (ciężar × powtórzenia) serii roboczych. */
@Composable
fun VolumeProgressChart(
    points: List<ExerciseStatsPoint>,
    modifier: Modifier = Modifier,
) {
    val columnColor = MaterialTheme.colorScheme.secondary
    val producer = remember { CartesianChartModelProducer() }

    LaunchedEffect(points) {
        val x = points.indices.map { index -> index as Number }
        producer.runTransaction {
            columnModel { series(x, points.map { point -> point.volumeKg as Number }) }
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                ColumnCartesianLayer.ColumnProvider.series(
                    rememberLineComponent(fill = Fill(columnColor), thickness = COLUMN_WIDTH),
                ),
            ),
            startAxis = VerticalAxis.rememberStart(valueFormatter = KILOGRAMS_FORMATTER),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = DateAxisFormatter(points)),
        ),
        modelProducer = producer,
        modifier = modifier.fillMaxWidth().height(CHART_HEIGHT),
    )
}

/** Legenda pod wykresem: kropka w kolorze linii i nazwa serii. */
@Composable
fun ChartLegend(
    entries: List<Pair<Color, String>>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        entries.forEach { (color, label) ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = CircleShape, color = color, modifier = Modifier.size(10.dp)) {}
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Legenda wykresu ciężaru - kolory muszą zgadzać się z [WeightProgressChart]. */
@Composable
fun weightLegend(showLightest: Boolean = false): List<Pair<Color, String>> = buildList {
    add(MaterialTheme.colorScheme.primary to stringResource(R.string.exercise_stats_record_heaviest))
    add(MaterialTheme.colorScheme.tertiary to stringResource(R.string.exercise_stats_legend_one_rep_max))
    if (showLightest) {
        add(MaterialTheme.colorScheme.secondary to stringResource(R.string.exercise_stats_legend_lightest))
    }
}

/** Data pod numerem treningu na osi X. */
private class DateAxisFormatter(
    private val points: List<ExerciseStatsPoint>,
) : CartesianValueFormatter {

    override fun format(
        context: CartesianMeasuringContext,
        value: Double,
        verticalAxisPosition: Axis.Position.Vertical?,
    ): CharSequence {
        val point = points.getOrNull(value.roundToInt()) ?: return ""
        return point.date.format(AXIS_DATE_FORMAT)
    }
}

private val COLUMN_WIDTH = 14.dp

/** Margines osi Y przy jednej wartości: co najmniej 2,5 kg albo 5% ciężaru. */
private const val MIN_RANGE_PADDING_KG = 2.5
private const val RANGE_PADDING_FRACTION = 0.05

private val KILOGRAMS_FORMATTER = CartesianValueFormatter.decimal(
    decimalCount = 0,
    thousandsSeparator = " ",
)
