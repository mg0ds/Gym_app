package com.gymtracker.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/**
 * Pionowa lista, której wiersze przestawia się przeciągnięciem
 * (przytrzymanie, potem ruch palcem).
 *
 * Świadomie nie używamy tu `LazyColumn`: listy dni i ćwiczeń dnia są krótkie,
 * a zwykła kolumna daje proste, przewidywalne liczenie pozycji - wiersz
 * przesuwa się o „jedną pozycję”, gdy przeciągnięcie przekroczy połowę wysokości
 * wiersza. Nową kolejność oddajemy raz, po upuszczeniu ([onMove]), więc baza nie
 * dostaje serii zmian w trakcie przeciągania.
 *
 * Wysokości wierszy mierzymy (mogą się różnić - wiersz z chipsami jest wyższy).
 * Do liczenia bierzemy wysokość **przeciąganego** wiersza.
 *
 * Uwaga na przyszłość: to, co liczymy w kompozycji (np. docelowy indeks), nie
 * nadaje się do odczytu w domknięciu gestu - `pointerInput` nie jest odtwarzany
 * przy każdej rekompozycji i trzymałoby wartość z momentu utworzenia. Dlatego
 * docelowy indeks liczymy w `onDragEnd` z aktualnego stanu, a [onMove] czytamy
 * przez [rememberUpdatedState].
 */
@Composable
fun ReorderableColumn(
    itemCount: Int,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    itemContent: @Composable (index: Int) -> Unit,
) {
    var draggedIndex by remember { mutableIntStateOf(NO_DRAG) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<Int, Float>() }
    val currentOnMove by rememberUpdatedState(onMove)
    val currentItemCount by rememberUpdatedState(itemCount)

    // Wysokość wiersza, po której liczymy przesunięcie: najpierw przeciąganego,
    // a gdy ten nie zdążył się zmierzyć - pierwszego zmierzonego.
    val rowHeight = heights[draggedIndex] ?: heights.values.firstOrNull() ?: 0f

    val targetIndex = targetIndexFor(
        fromIndex = draggedIndex,
        offsetPx = dragOffset,
        rowHeightPx = rowHeight,
        itemCount = itemCount,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        repeat(itemCount) { index ->
            val isDragged = index == draggedIndex
            val offsetY = when {
                isDragged -> dragOffset
                targetIndex == NO_DRAG -> 0f
                // Wiersze pomiędzy miejscem startu i upuszczenia ustępują o jedną pozycję.
                index in shiftedRange(draggedIndex, targetIndex) ->
                    if (draggedIndex < targetIndex) -rowHeight else rowHeight

                else -> 0f
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragged) 1f else 0f)
                    .offset { IntOffset(x = 0, y = offsetY.roundToInt()) }
                    .onSizeChanged { size ->
                        val measured = size.height.toFloat()
                        if (measured > 0f && heights[index] != measured) heights[index] = measured
                    }
                    .pointerInput(index, itemCount) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggedIndex = index
                                dragOffset = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount.y
                            },
                            onDragEnd = {
                                val from = draggedIndex
                                // Liczymy tutaj, a nie z kompozycji - patrz komentarz wyżej.
                                val to = targetIndexFor(
                                    fromIndex = from,
                                    offsetPx = dragOffset,
                                    rowHeightPx = heights[from] ?: rowHeight,
                                    itemCount = currentItemCount,
                                )
                                draggedIndex = NO_DRAG
                                dragOffset = 0f
                                if (from != NO_DRAG && to != NO_DRAG && from != to) {
                                    currentOnMove(from, to)
                                }
                            },
                            onDragCancel = {
                                draggedIndex = NO_DRAG
                                dragOffset = 0f
                            },
                        )
                    },
            ) {
                itemContent(index)
            }
        }
    }
}

/** Indeks, nad którym aktualnie jest przeciągany wiersz. */
private fun targetIndexFor(
    fromIndex: Int,
    offsetPx: Float,
    rowHeightPx: Float,
    itemCount: Int,
): Int {
    if (fromIndex == NO_DRAG || rowHeightPx <= 0f || itemCount <= 0) return NO_DRAG
    val movedBy = (offsetPx / rowHeightPx).roundToInt()
    return (fromIndex + movedBy).coerceIn(0, itemCount - 1)
}

/** Zakres indeksów, które przesuwają się wizualnie podczas przeciągania. */
private fun shiftedRange(from: Int, to: Int): IntRange =
    if (from < to) (from + 1)..to else to..(from - 1)

private const val NO_DRAG = -1
