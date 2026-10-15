package com.gymtracker.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Rozpoznawanie linków do filmów. Każda forma linku, jaką ludzie wklejają,
 * musi trafić w to samo miejsce (M7 dorysuje do tego miniatury).
 */
class YouTubeLinksTest {

    private val videoId = "tuwHzzPdaGc"

    @Test
    fun `rozpoznaje wszystkie typowe formy linku`() {
        val forms = listOf(
            "https://www.youtube.com/watch?v=$videoId",
            "https://youtube.com/watch?v=$videoId&t=42s",
            "https://youtu.be/$videoId",
            "https://www.youtube.com/embed/$videoId",
            "https://www.youtube.com/shorts/$videoId",
            "https://www.youtube.com/live/$videoId",
            videoId,
        )

        forms.forEach { form ->
            assertEquals("forma: $form", videoId, YouTubeLinks.idOf(form))
        }
    }

    @Test
    fun `normalizuje do jednej postaci`() {
        assertEquals(
            "https://www.youtube.com/watch?v=$videoId",
            YouTubeLinks.normalize("https://youtu.be/$videoId?si=abc123"),
        )
    }

    @Test
    fun `odrzuca to co nie jest filmem YouTube`() {
        assertNull(YouTubeLinks.idOf(null))
        assertNull(YouTubeLinks.idOf(""))
        assertNull(YouTubeLinks.idOf("   "))
        assertNull(YouTubeLinks.idOf("za-krotkie"))
        assertNull(YouTubeLinks.idOf("https://vimeo.com/123456789"))
        assertNull(YouTubeLinks.idOf("https://youtu.be/${videoId}x"))
    }

    @Test
    fun `zwykly link zapisujemy tak jak podal uzytkownik`() {
        val link = "https://drive.google.com/file/d/abc/view"

        assertEquals(link, YouTubeLinks.normalizeOrKeepRaw(link))
        assertNull(YouTubeLinks.normalize(link))
    }

    @Test
    fun `puste pole nie zapisuje linku`() {
        assertNull(YouTubeLinks.normalizeOrKeepRaw(null))
        assertNull(YouTubeLinks.normalizeOrKeepRaw(""))
        assertNull(YouTubeLinks.normalizeOrKeepRaw("   "))
    }

    @Test
    fun `link youtube z zewnatrz zapisujemy w wersji kanonicznej`() {
        assertEquals(
            "https://www.youtube.com/watch?v=$videoId",
            YouTubeLinks.normalizeOrKeepRaw("  youtu.be/$videoId  "),
        )
    }
}
