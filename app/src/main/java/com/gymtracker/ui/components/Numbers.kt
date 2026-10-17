package com.gymtracker.ui.components

/**
 * Liczby wpisywane i pokazywane w formularzach.
 *
 * W jednym miejscu, bo ten sam ciężar bywa wpisany w planie i w zalogowanej
 * serii: raz jako „62,5”, raz jako „62.5”, i oba muszą znaczyć to samo.
 */

/** „62.5” bez zbędnych zer końcowych („60”, a nie „60.0”); `null` bez wartości. */
fun Double?.toPlainStringOrNull(): String? = this?.let { value ->
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
}

/** Liczba z pola tekstowego: przecinek i kropka znaczą to samo. */
fun String.toDecimalOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()
