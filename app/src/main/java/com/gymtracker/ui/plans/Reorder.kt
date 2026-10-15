package com.gymtracker.ui.plans

/**
 * Nowa kolejność po przeciągnięciu wiersza z [from] na [to].
 *
 * Trzymamy to jako czystą funkcję, bo kolejność jest wrażliwa: po przeciągnięciu
 * do bazy idzie cała nowa lista identyfikatorów, a nie „przesuń o jeden”.
 */
internal fun <T> List<T>.moveItem(from: Int, to: Int): List<T> {
    if (from == to || from !in indices || to !in indices) return this
    val result = toMutableList()
    result.add(to, result.removeAt(from))
    return result
}
