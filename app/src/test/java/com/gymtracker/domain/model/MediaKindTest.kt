package com.gymtracker.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rodzaj załącznika: rozpoznanie pliku po typie MIME albo po nazwie.
 *
 * Nazwa jest planem B dla dostawców, którzy nie podają typu (`null`) - bez tego
 * wybrany plik wracałby jako „nie jest zdjęciem ani filmem”.
 */
class MediaKindTest {

    @Test
    fun `typ mime decyduje o rodzaju`() {
        assertEquals(MediaKind.IMAGE, MediaKind.ofMimeType("image/png"))
        assertEquals(MediaKind.IMAGE, MediaKind.ofMimeType("image/heic"))
        assertEquals(MediaKind.VIDEO, MediaKind.ofMimeType("video/mp4"))
        assertEquals(MediaKind.VIDEO, MediaKind.ofMimeType("video/quicktime"))
    }

    @Test
    fun `inny typ pliku nie jest zalacznikiem`() {
        assertNull(MediaKind.ofMimeType("application/pdf"))
        assertNull(MediaKind.ofMimeType("text/plain"))
    }

    @Test
    fun `bez typu mime ratuje nas rozszerzenie nazwy`() {
        assertEquals(MediaKind.IMAGE, MediaKind.ofMimeType(null, "IMG_2026.JPG"))
        assertEquals(MediaKind.IMAGE, MediaKind.ofMimeType(null, "zdjecie.webp"))
        assertEquals(MediaKind.VIDEO, MediaKind.ofMimeType(null, "trening.mov"))
        assertEquals(MediaKind.VIDEO, MediaKind.ofMimeType(null, "clip.MP4"))
        assertNull(MediaKind.ofMimeType(null, "notatki.pdf"))
        assertNull(MediaKind.ofMimeType(null, null))
        assertNull(MediaKind.ofMimeType(null, "bezKropki"))
    }

    @Test
    fun `typ mime ma pierwszeństwo nad nazwa`() {
        // Dostawca mówi wprost, co to jest - nie zgadujemy z rozszerzenia.
        assertEquals(MediaKind.VIDEO, MediaKind.ofMimeType("video/mp4", "film.jpg"))
    }

    @Test
    fun `rodzaj z bazy czyta sie tylko dla znanego slowa`() {
        assertEquals(MediaKind.IMAGE, MediaKind.fromDb("image"))
        assertEquals(MediaKind.VIDEO, MediaKind.fromDb("video"))
        assertEquals(MediaKind.YOUTUBE, MediaKind.fromDb("youtube"))
        assertEquals(MediaKind.LINK, MediaKind.fromDb("link"))
        assertNull(MediaKind.fromDb("bogus"))
        assertNull(MediaKind.fromDb(null))
    }

    @Test
    fun `plikiem z galerii sa tylko zdjecia i filmy`() {
        assertTrue(MediaKind.IMAGE.isGalleryFile)
        assertTrue(MediaKind.VIDEO.isGalleryFile)
        assertFalse(MediaKind.YOUTUBE.isGalleryFile)
        assertFalse(MediaKind.LINK.isGalleryFile)
    }
}
