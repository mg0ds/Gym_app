package com.gymtracker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.gymtracker.domain.model.ThemeMode

/**
 * Motyw aplikacji. Od Androida 12 korzystamy z kolorów systemowych (Material You),
 * na starszych - z palety z [Color.kt].
 *
 * @param themeMode wybór z Ustawień (system / jasny / ciemny).
 */
@Composable
fun GymTrackerTheme(
    themeMode: ThemeMode = ThemeMode.DEFAULT,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> GymDarkColors
        else -> GymLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = GymTypography,
        content = content,
    )
}
