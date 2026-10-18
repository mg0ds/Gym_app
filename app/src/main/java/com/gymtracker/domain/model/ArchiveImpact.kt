package com.gymtracker.domain.model

/**
 * Co zabierze opróżnienie archiwum (M9).
 *
 * To nie jest zwykła liczba ćwiczeń: archiwizacja istnieje po to, żeby plany
 * i historia zostały czytelne, więc pytanie brzmi „co jeszcze zniknie razem
 * z nimi?”. Odpowiedź musi paść **przed** kasowaniem - po nim nie ma czego
 * odtworzyć.
 */
data class ArchiveImpact(
    val exercises: Int = 0,
    val planPositions: Int = 0,
    val sessionPositions: Int = 0,
    val media: Int = 0,
) {
    val isEmpty: Boolean get() = exercises == 0

    /**
     * Czy kasowanie sięga poza samo archiwum (plany, historię, załączniki).
     * Gdy sięga, pytanie musi to powiedzieć wprost - inaczej użytkownik
     * kasuje „puste” ćwiczenia i traci razem z nimi treningi.
     */
    val touchesDataElsewhere: Boolean
        get() = planPositions > 0 || sessionPositions > 0 || media > 0
}
