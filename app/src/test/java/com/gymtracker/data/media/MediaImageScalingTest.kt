package com.gymtracker.data.media

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Dobór próbkowania przy dekodowaniu zdjęcia na miniaturę.
 *
 * `inSampleSize` to jedyny sposób, żeby duże zdjęcie nie zajęło całej pamięci
 * przy dekodowaniu - a musi być potęgą dwójki i nie może przeskoczyć poniżej
 * rozmiaru miniatury (inaczej kafelek byłby rozmyty bardziej niż trzeba).
 */
class MediaImageScalingTest {

    @Test
    fun `duze zdjecie probkujemy tak dlugo jak sie da bez zejscia pod miniature`() {
        // 4000 → 2000 → 1000 → 500 → 250 (wciąż >= 200), więc 16x.
        assertEquals(16, sampleSize(width = 4000, height = 3000, maxPx = 200))
    }

    @Test
    fun `probkowanie nie schodzi ponizej rozmiaru miniatury`() {
        // 400 → 200 (jeszcze mieści się pod 200 px), dalej 100 byłoby za mało.
        assertEquals(2, sampleSize(width = 400, height = 300, maxPx = 200))
    }

    @Test
    fun `male zdjecie zostaje w oryginale`() {
        assertEquals(1, sampleSize(width = 100, height = 80, maxPx = 200))
    }

    @Test
    fun `nieznane wymiary nie psuja dekodowania`() {
        assertEquals(1, sampleSize(width = 0, height = 0, maxPx = 200))
        assertEquals(1, sampleSize(width = -1, height = 500, maxPx = 200))
    }
}
