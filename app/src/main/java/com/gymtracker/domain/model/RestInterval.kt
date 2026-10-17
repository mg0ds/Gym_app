package com.gymtracker.domain.model

/** Dlaczego przerwa się skończyła. */
enum class RestEndReason(val wire: String) {
    /** Odliczanie dobiegło końca albo użytkownik wrócił do pracy. */
    COMPLETED("completed"),

    /** To samo, ale cel był wydłużony „+15 s”. */
    EXTENDED("extended"),

    /** Użytkownik uznał, że przerwa nie jest potrzebna. */
    SKIPPED("skipped"),

    /** Przerwana bez decyzji o przerwie (pauza, koniec treningu, nowa przerwa). */
    CANCELLED("cancelled"),
    ;

    /**
     * Czy przerwa mówi coś o formie, czyli czy wchodzi do średniej.
     *
     * „Pomiń" i „przerwana" są decyzją o braku przerwy, a nie pomiarem przerwy -
     * wliczone zaniżałyby wynik.
     */
    val countsToStats: Boolean
        get() = this == COMPLETED || this == EXTENDED

    companion object {
        fun fromWire(value: String?): RestEndReason? =
            entries.firstOrNull { reason -> reason.wire == value }
    }
}

/**
 * Jedna przerwa między seriami: kiedy się zaczęła, ile trwała i jak się
 * skończyła.
 *
 * Średnie i podsumowania liczymy z tej tabeli, a nie z `set_logs.rest_after_s`,
 * bo tylko tutaj widać **powód** zakończenia - przerwa pominięta albo przerwana
 * nie mówi nic o formie i nie powinna zaniżać średniej.
 */
data class RestInterval(
    val id: Long,
    val exerciseId: Long,
    val sessionId: Long?,
    val setLogId: Long?,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long?,
    val plannedSeconds: Int?,
    val actualSeconds: Int?,
    val reason: RestEndReason?,
    val isAuto: Boolean,
) {

    val isRunning: Boolean get() = endedAtEpochMs == null

    /** Ile przerwy minęło o [nowEpochMs] (dla zamkniętej - ile trwała). */
    fun elapsedMs(nowEpochMs: Long): Long =
        ((endedAtEpochMs ?: nowEpochMs) - startedAtEpochMs).coerceAtLeast(0L)

    /** Czy przerwa wchodzi do średniej (pominięte i przerwane odpadają). */
    val countsToStats: Boolean
        get() = reason == RestEndReason.COMPLETED || reason == RestEndReason.EXTENDED
}
