package com.gymtracker.ui.theme

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.gymtracker.domain.model.ThemeMode

/**
 * Motyw aplikacji: paleta z [Color.kt] plus własne kształty i typografia.
 *
 * Kolory systemowe (Material You) są **wyłączone**: aplikacja ma wyglądać tak
 * samo na każdym urządzeniu, a tapeta użytkownika nie może przemalować
 * kalendarza ani wykresów (czerwień niesie tu znaczenie: rekord, błąd).
 * [dynamicColor] zostaje jako parametr dla testów i przyszłej opcji.
 *
 * @param themeMode wybór z Ustawień (system / jasny / ciemny).
 */
@Composable
fun GymTrackerTheme(
    themeMode: ThemeMode = ThemeMode.DEFAULT,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicScheme(darkTheme)
        darkTheme -> GymDarkColors
        else -> GymLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = GymTypography,
        shapes = GymShapes,
        content = content,
    )
}

/** Kolory z tapety (Material You); tylko Android 12+ - inaczej paleta aplikacji. */
@RequiresApi(Build.VERSION_CODES.S)
@Composable
private fun dynamicScheme(darkTheme: Boolean): ColorScheme {
    val context = LocalContext.current
    return if (darkTheme) {
        dynamicDarkColorScheme(context)
    } else {
        dynamicLightColorScheme(context)
    }
}
