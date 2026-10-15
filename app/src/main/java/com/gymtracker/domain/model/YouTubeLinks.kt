package com.gymtracker.domain.model

/**
 * Rozpoznawanie i normalizacja linków do filmów.
 *
 * Ze źródeł danych przychodzą same identyfikatory (`youtube_id`), a od
 * użytkownika dowolna forma linku - dlatego wszystko sprowadzamy do jednej
 * postaci `https://www.youtube.com/watch?v=ID`.
 *
 * Link, którego nie rozpoznamy jako YouTube, nie jest błędem: zapisujemy go
 * tak, jak podał użytkownik (może to być np. własny film w chmurze).
 */
object YouTubeLinks {

    private const val WATCH_URL_PREFIX = "https://www.youtube.com/watch?v="

    /** ID filmu YouTube: dokładnie 11 znaków `[A-Za-z0-9_-]`. */
    private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")

    private val URL_PATTERNS = listOf(
        Regex("""youtube\.com/watch\?[^#]*\bv=([A-Za-z0-9_-]{11})(?![\w-])"""),
        Regex("""youtu\.be/([A-Za-z0-9_-]{11})(?![\w-])"""),
        Regex("""youtube\.com/embed/([A-Za-z0-9_-]{11})(?![\w-])"""),
        Regex("""youtube\.com/shorts/([A-Za-z0-9_-]{11})(?![\w-])"""),
        Regex("""youtube\.com/live/([A-Za-z0-9_-]{11})(?![\w-])"""),
    )

    /** Wyciąga ID filmu z linku albo z samego ID; `null`, gdy się nie da. */
    fun idOf(input: String?): String? {
        val candidate = input?.trim().orEmpty()
        if (candidate.isEmpty()) return null
        if (VIDEO_ID.matches(candidate)) return candidate
        return URL_PATTERNS.firstNotNullOfOrNull { pattern ->
            pattern.find(candidate)?.groupValues?.get(1)
        }
    }

    /** Kanoniczny link do filmu; `null`, gdy to nie jest link YouTube. */
    fun normalize(input: String?): String? = idOf(input)?.let(::watchUrl)

    /**
     * Co zapisać w bazie: kanoniczny link YouTube, a gdy to nie YouTube -
     * to, co podał użytkownik (pusty tekst daje `null`).
     */
    fun normalizeOrKeepRaw(input: String?): String? {
        val raw = input?.trim().orEmpty()
        if (raw.isEmpty()) return null
        return normalize(raw) ?: raw
    }

    fun watchUrl(videoId: String): String = WATCH_URL_PREFIX + videoId
}
