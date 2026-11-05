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
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
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
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Wykresy postępu (Vico).
 *
 * Trzy decyzje, które łatwo pomylić z usterką:
 *
 * 1. **Oś X to kolejne treningi, nie kalendarz.** Na wykresie postępu pytanie
 *    brzmi „czy rosnę z treningu na trening”; przy rzadkich treningach
 *    proporcjonalna oś ścisnęłaby punkty w kupkę na jednym końcu. Datę każdego
 *    punktu widać pod wykresem, w tabeli wykonań.
 * 2. **Punkt bez ciężaru wypada z wykresu ciężaru**, a nie jest rysowany jako
 *    zero: zero znaczyłoby „podniosłem nic”, a to nieprawda.
 * 3. **Oś Y ma „ładne” wartości.** Automatyczny zakres Vico zaczyna etykiety od
 *    surowego minimum (np. 32) i dokłada krok 10, więc przy 40 kg na osi
 *    pojawiało się 32, 42, 52… Tutaj zakres i krok liczymy sami, żeby etykiety
 *    wypadały na pełnych kilogramach (30, 40, 50).
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

    val yAxis = yAxisPlan(
        values = weightPoints.flatMap { point ->
            listOfNotNull(point.topSetKg, point.minSetKg, point.estimatedOneRepMaxKg)
        },
        // Na siłowni ciężar zmienia się skokowo co 2,5-5 kg; oś co 2 kg
        // wyglądałaby na przypadkową siatkę.
        minStep = 5.0,
    )

    ChartTheme {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(lines),
                    rangeProvider = yAxis.rangeProvider(),
                ),
                startAxis = verticalAxis(yAxis, KILOGRAMS_FORMATTER),
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = DateAxisFormatter(weightPoints),
                    label = rememberAxisLabelComponent(
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ),
                    guideline = rememberAxisGuidelineComponent(
                        fill = Fill(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    ),
                ),
            ),
            modelProducer = producer,
            modifier = modifier.fillMaxWidth().height(CHART_HEIGHT),
        )
    }
}

/** Znacznik punktu - bez niego pojedynczy trening jest niewidoczny. */
@Composable
private fun marker(color: Color): LineCartesianLayer.Point = LineCartesianLayer.Point(
    component = rememberShapeComponent(fill = Fill(color), shape = CircleShape),
    size = 6.dp,
)

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

    val yAxis = yAxisPlan(points.map { point -> point.volumeKg })

    ChartTheme {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(
                    ColumnCartesianLayer.ColumnProvider.series(
                        rememberLineComponent(fill = Fill(columnColor), thickness = COLUMN_WIDTH),
                    ),
                ),
                startAxis = verticalAxis(yAxis, KILOGRAMS_FORMATTER),
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = DateAxisFormatter(points),
                    label = rememberAxisLabelComponent(
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ),
                    guideline = rememberAxisGuidelineComponent(
                        fill = Fill(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    ),
                ),
            ),
            modelProducer = producer,
            modifier = modifier.fillMaxWidth().height(CHART_HEIGHT),
        )
    }
}

/**
 * Motyw Vico zsynchronizowany z motywem aplikacji.
 *
 * Domyślnie Vico bierze jasny/ciemny wariant z **motywu systemu**, nie z
 * `GymTrackerTheme`. Przy wymuszonym jasnym widoku i ciemnym telefonie etykiety
 * osi robiły się białe na jasnym tle. `rememberM3VicoTheme` czyta wprost
 * `MaterialTheme.colorScheme` (m.in. `onBackground`).
 */
@Composable
private fun ChartTheme(content: @Composable () -> Unit) {
    ProvideVicoTheme(theme = rememberM3VicoTheme(), content = content)
}

/** Oś Y: pełne wartości, delikatna siatka i wspólny formatter. */
@Composable
private fun verticalAxis(
    plan: YAxisPlan,
    formatter: CartesianValueFormatter,
): VerticalAxis<Axis.Position.Vertical.Start> = VerticalAxis.rememberStart(
    valueFormatter = formatter,
    itemPlacer = remember(plan.step) { VerticalAxis.ItemPlacer.step({ plan.step }) },
    label = rememberAxisLabelComponent(
        style = MaterialTheme.typography.labelSmall.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ),
    guideline = rememberAxisGuidelineComponent(
        fill = Fill(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ),
)

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

/**
 * Zakres i krok osi Y policzone razem, żeby etykiety nie wypadały „obok”
 * danych. `step` jest wielokrotnością 1, 2, 2,5 albo 5 razy potęga dziesiątki.
 */
internal data class YAxisPlan(
    val minY: Double,
    val maxY: Double,
    val step: Double,
) {
    fun rangeProvider(): CartesianLayerRangeProvider =
        CartesianLayerRangeProvider.fixed(minY = minY, maxY = maxY)
}

internal fun yAxisPlan(values: List<Double>, minStep: Double = 0.0): YAxisPlan {
    val finite = values.filter { value -> value.isFinite() }
    val dataMin = finite.minOrNull() ?: 0.0
    val dataMax = finite.maxOrNull() ?: 1.0
    val span = (dataMax - dataMin).coerceAtLeast(max(dataMax * 0.1, 1.0))
    // Oddech nad danymi ma być mały: przy 8% i kroku 20 kg oś schodziła
    // o dwa „oczka” poniżej najlżejszej serii (20 zamiast 40).
    val padding = max(span * 0.04, if (dataMax <= 50) 1.0 else 0.0)
    // Celujemy w ~5 przedziałów: przy czterech zbyt łatwo wypada krok 50,
    // który przy danych 45–117 kg daje niepotrzebne 0 na dole osi.
    val rawStep = (span + padding * 2) / 5
    val step = max(niceStep(rawStep), minStep)
    var low = floor((dataMin - padding) / step) * step
    var high = ceil((dataMax + padding) / step) * step
    if (low < 0.0 && dataMin >= 0.0) low = 0.0
    if (high <= low) high = low + step
    return YAxisPlan(minY = low, maxY = high, step = step)
}

private fun niceStep(raw: Double): Double {
    if (raw <= 0.0 || !raw.isFinite()) return 1.0
    val magnitude = 10.0.pow(floor(log10(raw)))
    val normalized = raw / magnitude
    val nice = when {
        normalized <= 1.0 -> 1.0
        normalized <= 2.0 -> 2.0
        normalized <= 5.0 -> 5.0
        else -> 10.0
    }
    return nice * magnitude
}

/** Czy w zakresie jest rampa: najlżejsza seria lżejsza od najcięższej. */
fun hasWeightRange(points: List<ExerciseStatsPoint>): Boolean = points.any { point ->
    val lightest = point.minSetKg
    val heaviest = point.topSetKg
    lightest != null && heaviest != null && lightest < heaviest
}

private val COLUMN_WIDTH = 14.dp

private val KILOGRAMS_FORMATTER = CartesianValueFormatter.decimal(
    decimalCount = 0,
    thousandsSeparator = " ",
)
