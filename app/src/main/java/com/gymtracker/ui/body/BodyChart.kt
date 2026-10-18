package com.gymtracker.ui.body

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gymtracker.domain.body.BodyPoint
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToLong

/**
 * Wykres zmiany wskaźnika (Vico).
 *
 * Jedna decyzja różni go od wykresów postępu ćwiczenia: **oś X to czas, a nie
 * numer pomiaru**. Tam pytanie brzmiało „czy rosnę z treningu na trening”, więc
 * kolejne treningi były równo oddalone. Tu pytanie brzmi „co się zmieniło
 * w ciągu trzech miesięcy”, więc trzymiesięczna przerwa w mierzeniu musi być
 * widoczna jako przerwa, a nie zniknąć między dwoma sąsiednimi punktami.
 *
 * Cel rysujemy jako drugą linię o stałej wartości - dokładnie tak, jak wygląda
 * na wykresie: horyzont, do którego się dąży.
 *
 * Dwie rzeczy trzeba ustawić wbrew domyślnym wartościom Vico, bo przy dacie na
 * osi X wychodzą na wierzch (na wykresach ćwiczeń, gdzie X to numer treningu,
 * nie było ich widać):
 *
 * 1. **Dopasowanie do szerokości.** Domyślny zoom startowy to
 *    `max(fixed(1), Content)`, czyli „1” dla treści szerszej niż ekran. Treść ma
 *    szerokość stałej liczby pikseli na jednostkę X (`pointSpacing`), więc trzy
 *    miesiące pomiarów to kilkadziesiąt dni razy te piksele - ekran pokazywał
 *    pierwszych kilka dni, a resztę trzeba było wygarniać palcem. Stąd
 *    `Zoom.Content` na sztywno i brak gestów zoomu: okno wykresu ustawiają chipy
 *    zakresu (1M/3M/…), więc wykres nie ma prawa uciekać spod palca.
 * 2. **Zakres osi wartości od pomiarów, nie od zera** ([bodyValueAxis]). Waga
 *    narysowana od zera jest linią przy górnej krawędzi - czyli brak wykresu
 *    tam, gdzie ma być widać zmianę.
 */

/** Wysokość wykresu - tyle, by osie i etykiety były czytelne, a ekran nie tonął w jednym wykresie. */
private val CHART_HEIGHT = 180.dp

/** „12.10” na osi X; pełne daty są na liście pomiarów. */
private val AXIS_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d.MM")

/**
 * Etykiety osi wartości: jedno miejsce po przecinku i bez zer końcowych („82.5”, „83”).
 * Dokładne liczby są w podsumowaniu i na liście pomiarów.
 */
private val VALUE_FORMATTER = CartesianValueFormatter.decimal(
    decimalCount = 1,
    thousandsSeparator = " ",
)

/** Ile odstępów między etykietami osi wartości chcemy widzieć. */
private const val VALUE_AXIS_INTERVALS = 4

/** Rozpiętość podstawiona, gdy wszystkie pomiary są równe - pomiar wypada wtedy na środku. */
private const val FLAT_VALUE_SPAN = 1.0

/**
 * Tolerancja przy przycinaniu zakresu do kroku. Bez niej `39.9 / 0.1` daje
 * `398.99999999999994`, a dolna krawędź spada o cały krok (39.8 zamiast 39.9).
 */
private const val SNAP_EPSILON = 1e-9

/** Zakres osi wartości razem z krokiem etykiet. */
internal data class BodyValueAxis(
    val range: ClosedFloatingPointRange<Double>,
    val step: Double,
)

/**
 * Osie wartości wykresu: zakres od najniższej do najwyższej narysowanej liczby
 * (pomiary i linia celu) oraz krok etykiet.
 *
 * Zakres **nie startuje od zera**: waga 82.5-84 kg od zera to linia prosta przy
 * górnej krawędzi, czyli brak wykresu tam, gdzie ma być widać zmianę.
 *
 * Krok jest „okrągły” (1, 2 albo 5 razy potęga dziesiątki), a zakres przycięty do
 * jego wielokrotności, bo Vico rysuje etykiety od dolnej krawędzi zakresu: bez
 * przycięcia wyszedłby rząd „82.35, 83.35”.
 */
internal fun bodyValueAxis(minY: Double, maxY: Double): BodyValueAxis {
    val low = min(minY, maxY)
    val high = max(minY, maxY)
    val measured = high > low
    val bottom = if (measured) low else low - FLAT_VALUE_SPAN / 2
    val top = if (measured) high else low + FLAT_VALUE_SPAN / 2
    val step = niceStep((top - bottom) / VALUE_AXIS_INTERVALS)
    return BodyValueAxis(
        range = clean(floor(bottom / step + SNAP_EPSILON) * step)..
            clean(ceil(top / step - SNAP_EPSILON) * step),
        step = step,
    )
}

/**
 * Obcina ogon zmiennoprzecinkowy (`39.900000000000006` → `39.9`).
 *
 * Zakres trafia potem do etykiet i do testów, a `399 * 0.1` w `Double` nie jest
 * równe `39.9` - bez tego dolna krawędź bywa o włos **poniżej** pomiaru i wygląda
 * jak zgubiony punkt.
 */
private fun clean(value: Double): Double = round(value * 1e9) / 1e9

/** Najbliższy „okrągły” krok: 1, 2 albo 5 razy potęga dziesiątki (0.05, 0.5, 5, 50…). */
private fun niceStep(raw: Double): Double {
    val magnitude = 10.0.pow(floor(log10(raw)))
    val normalized = raw / magnitude
    return magnitude * when {
        normalized <= 1.0 -> 1.0
        normalized <= 2.0 -> 2.0
        normalized <= 5.0 -> 5.0
        else -> 10.0
    }
}

@Composable
fun BodyProgressChart(
    points: List<BodyPoint>,
    target: Double?,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val targetColor = MaterialTheme.colorScheme.tertiary
    val producer = remember { CartesianChartModelProducer() }

    // Ten sam rachunek co w `BodyValueRangeProvider`, tylko z listy punktów: zakres
    // rysuje warstwa (z modelu), a krok etykiet musi być znany tutaj.
    val axis = remember(points, target) {
        val values = points.map { point -> point.value } + listOfNotNull(target)
        if (values.isEmpty()) bodyValueAxis(0.0, 1.0) else bodyValueAxis(values.min(), values.max())
    }
    val itemPlacer = remember(axis.step) {
        VerticalAxis.ItemPlacer.step(step = { axis.step })
    }

    LaunchedEffect(points, target) {
        // Dzień jako liczba (`epochDay`) daje odstępy proporcjonalne do czasu.
        val x = points.map { point -> point.date.toEpochDay() as Number }
        val values = points.map { point -> point.value as Number }
        producer.runTransaction {
            lineModel {
                series(x, values)
                if (target != null) {
                    series(x, List(points.size) { target as Number })
                }
            }
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                LineCartesianLayer.LineProvider.series(
                    listOfNotNull(
                        LineCartesianLayer.Line(LineCartesianLayer.LineFill.single(Fill(lineColor))),
                        target?.let {
                            LineCartesianLayer.Line(
                                LineCartesianLayer.LineFill.single(Fill(targetColor)),
                            )
                        },
                    ),
                ),
                rangeProvider = BodyValueRangeProvider,
            ),
            startAxis = VerticalAxis.rememberStart(
                valueFormatter = VALUE_FORMATTER,
                itemPlacer = itemPlacer,
            ),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = DateAxisFormatter),
        ),
        modelProducer = producer,
        zoomState = rememberVicoZoomState(
            zoomEnabled = false,
            initialZoom = Zoom.Content,
            minZoom = Zoom.Content,
            maxZoom = Zoom.Content,
        ),
        modifier = modifier.fillMaxWidth().height(CHART_HEIGHT),
    )
}

/** Wpina [bodyValueAxis] w Vico - zakres liczy się z pomiarów, nie od zera. */
private object BodyValueRangeProvider : CartesianLayerRangeProvider {

    override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore): Double =
        bodyValueAxis(minY, maxY).range.start

    override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double =
        bodyValueAxis(minY, maxY).range.endInclusive
}

/** Wartość osi X (dzień od 1970) → „12.10”. Poza zakresem dat nic nie piszemy. */
private object DateAxisFormatter : CartesianValueFormatter {

    override fun format(
        context: CartesianMeasuringContext,
        value: Double,
        verticalAxisPosition: Axis.Position.Vertical?,
    ): CharSequence = runCatching {
        LocalDate.ofEpochDay(value.roundToLong()).format(AXIS_DATE_FORMAT)
    }.getOrDefault("")
}
