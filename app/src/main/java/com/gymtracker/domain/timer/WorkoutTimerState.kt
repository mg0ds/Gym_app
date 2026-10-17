package com.gymtracker.domain.timer

/**
 * Zegar treningu: kiedy się zaczął i ile czasu stał w pauzie.
 *
 * Cała arytmetyka zegara siedzi w tej jednej, czystej klasie, bo liczy się ona
 * w trzech miejscach: na ekranie treningu, w usłudze w tle (notyfikacja)
 * i po restarcie procesu. Jedno miejsce = jedna odpowiedź na pytanie „ile już
 * trwa ten trening”.
 *
 * Model jest **zapisywalny w całości**: `startedAt` + suma pauz + otwarta pauza
 * to trzy liczby, które trafiają do `sessions`. Dzięki temu zabicie aplikacji
 * w trakcie pauzy nic nie psuje - po restarcie zegar wstaje dokładnie tam,
 * gdzie był, i nie zamienia pauzy w czas treningu.
 *
 * Czas liczymy z zegara ściennego (epoch), nie z `elapsedRealtime`. To
 * świadomy wybór: kotwica monotoniczna żyje tylko w procesie, więc po restarcie
 * i tak trzeba wrócić do zegara ściennego - a dwa wzory na ten sam czas
 * rozjechałyby się przy pierwszej pauzie. Cena jest jedna: ręczna zmiana
 * godziny w telefonie przesuwa licznik (o tyle samo myli się `startedAt`).
 */
data class WorkoutTimerState(
    val startedAtEpochMs: Long,
    val pausedTotalMs: Long = 0L,
    /** Początek otwartej pauzy albo `null`, gdy zegar idzie. */
    val pausedAtEpochMs: Long? = null,
) {

    val isPaused: Boolean get() = pausedAtEpochMs != null

    /**
     * Czas treningu (bez pauz) o [nowEpochMs].
     *
     * W pauzie czas stoi: liczymy do momentu jej rozpoczęcia, więc ekran
     * i notyfikacja pokazują zamrożoną wartość.
     */
    fun elapsedMs(nowEpochMs: Long): Long {
        val until = pausedAtEpochMs ?: nowEpochMs
        return (until - startedAtEpochMs - pausedTotalMs).coerceAtLeast(0L)
    }

    /** Wstrzymuje zegar. Powtórne wstrzymanie nic nie zmienia. */
    fun paused(nowEpochMs: Long): WorkoutTimerState =
        if (isPaused) this else copy(pausedAtEpochMs = nowEpochMs)

    /** Wznawia zegar, doliczając pauzę do [pausedTotalMs]. */
    fun resumed(nowEpochMs: Long): WorkoutTimerState = pausedAtEpochMs?.let { startedAt ->
        copy(
            pausedTotalMs = pausedTotalMs + (nowEpochMs - startedAt).coerceAtLeast(0L),
            pausedAtEpochMs = null,
        )
    } ?: this
}

/**
 * Trening pilnowany przez timer: czego dotyczy zegar i w jakim jest stanie.
 * Tę informację pokazuje nagłówek ekranu treningu i notyfikacja.
 */
data class TrackedWorkout(
    val sessionId: Long,
    val name: String,
    val timer: WorkoutTimerState,
) {

    /** „42:10” albo „1:05:12” - format wspólny dla ekranu i notyfikacji. */
    fun elapsedText(nowEpochMs: Long): String = formatElapsed(timer.elapsedMs(nowEpochMs))
}

/**
 * Format czasu treningu: `1:05:12`, a poniżej godziny `42:10`.
 *
 * Trzymamy to w domenie, a nie w UI, bo ten sam tekst trafia do notyfikacji
 * (bez Compose) i na ekran. Jedno miejsce = identyczny zapis w obu.
 */
fun formatElapsed(elapsedMs: Long): String {
    val totalSeconds = (elapsedMs / 1_000).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
