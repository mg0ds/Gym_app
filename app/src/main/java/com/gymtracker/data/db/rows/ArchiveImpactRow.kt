package com.gymtracker.data.db.rows

import androidx.room.ColumnInfo

/**
 * Ile zniknie razem z archiwum, gdy użytkownik je opróżni.
 *
 * Wszystkie liczby liczy **jedno** zapytanie z podzapytaniami: i tak trafiają do
 * jednego zdania w pytaniu przed kasowaniem, a osobne zapytania przebiegałyby
 * cztery razy po tych samych tabelach. Poza ćwiczeniami liczymy to, co wisi na
 * nich kaskadą: pozycje w planach, wpisy w historii treningów i załączniki.
 * Serii i przerw nie liczymy osobno - znikają razem z wpisem historii, a większa
 * liczba nie zmieniłaby decyzji użytkownika (rośnie w tę samą stronę).
 */
data class ArchiveImpactRow(
    @ColumnInfo(name = "exercises")
    val exercises: Int,
    @ColumnInfo(name = "plan_positions")
    val planPositions: Int,
    @ColumnInfo(name = "session_positions")
    val sessionPositions: Int,
    @ColumnInfo(name = "media")
    val media: Int,
)
