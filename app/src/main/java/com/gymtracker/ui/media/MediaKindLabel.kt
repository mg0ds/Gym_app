package com.gymtracker.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gymtracker.R
import com.gymtracker.domain.model.MediaKind

/**
 * „Zdjęcie” / „Film” / „Link” - jedno miejsce decyduje, jak nazywamy rodzaj
 * załącznika. Nazwy używa i tytuł podglądu, i opis kafelka dla czytnika ekranu.
 */
@Composable
internal fun MediaKind.label(): String = stringResource(
    when (this) {
        MediaKind.IMAGE -> R.string.media_kind_photo
        MediaKind.VIDEO -> R.string.media_kind_video
        MediaKind.YOUTUBE, MediaKind.LINK -> R.string.media_kind_link
    },
)
