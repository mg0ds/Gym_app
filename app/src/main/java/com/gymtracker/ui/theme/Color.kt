package com.gymtracker.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Paleta zapasowa (gdy urządzenie nie wspiera dynamic color, czyli < Android 12).
// Wzorowana na Material 3 "blue" - spokojna, czytelna w siłowni.

internal val GymBlue40 = Color(0xFF00639B)
internal val GymBlue80 = Color(0xFF96CCFF)
internal val GymBlueContainerLight = Color(0xFFCDE5FF)
internal val GymBlueContainerDark = Color(0xFF004A79)
internal val GymBlueOnContainerLight = Color(0xFF001D33)
internal val GymBlueOnContainerDark = Color(0xFFCDE5FF)

internal val GymSlate40 = Color(0xFF51606F)
internal val GymSlate80 = Color(0xFFB8C8DA)
internal val GymSlateContainerLight = Color(0xFFD4E4F6)
internal val GymSlateContainerDark = Color(0xFF394857)
internal val GymSlateOnContainerLight = Color(0xFF0D1D2A)
internal val GymSlateOnContainerDark = Color(0xFFD4E4F6)

internal val GymViolet40 = Color(0xFF67587A)
internal val GymViolet80 = Color(0xFFD3BFE7)

internal val GymSurfaceLight = Color(0xFFFCFCFF)
internal val GymSurfaceDark = Color(0xFF1A1C1E)
internal val GymOnSurfaceLight = Color(0xFF1A1C1E)
internal val GymOnSurfaceDark = Color(0xFFE2E2E6)
internal val GymSurfaceVariantLight = Color(0xFFDEE3EB)
internal val GymSurfaceVariantDark = Color(0xFF42474E)
internal val GymOnSurfaceVariantLight = Color(0xFF42474E)
internal val GymOnSurfaceVariantDark = Color(0xFFC2C7CF)

internal val GymErrorLight = Color(0xFFBA1A1A)
internal val GymErrorDark = Color(0xFFFFB4AB)

internal val GymLightColors = lightColorScheme(
    primary = GymBlue40,
    onPrimary = Color.White,
    primaryContainer = GymBlueContainerLight,
    onPrimaryContainer = GymBlueOnContainerLight,
    secondary = GymSlate40,
    onSecondary = Color.White,
    secondaryContainer = GymSlateContainerLight,
    onSecondaryContainer = GymSlateOnContainerLight,
    tertiary = GymViolet40,
    onTertiary = Color.White,
    background = GymSurfaceLight,
    onBackground = GymOnSurfaceLight,
    surface = GymSurfaceLight,
    onSurface = GymOnSurfaceLight,
    surfaceVariant = GymSurfaceVariantLight,
    onSurfaceVariant = GymOnSurfaceVariantLight,
    error = GymErrorLight,
    onError = Color.White,
)

internal val GymDarkColors = darkColorScheme(
    primary = GymBlue80,
    onPrimary = Color(0xFF003354),
    primaryContainer = GymBlueContainerDark,
    onPrimaryContainer = GymBlueOnContainerDark,
    secondary = GymSlate80,
    onSecondary = Color(0xFF23313F),
    secondaryContainer = GymSlateContainerDark,
    onSecondaryContainer = GymSlateOnContainerDark,
    tertiary = GymViolet80,
    onTertiary = Color(0xFF372948),
    background = GymSurfaceDark,
    onBackground = GymOnSurfaceDark,
    surface = GymSurfaceDark,
    onSurface = GymOnSurfaceDark,
    surfaceVariant = GymSurfaceVariantDark,
    onSurfaceVariant = GymOnSurfaceVariantDark,
    error = GymErrorDark,
    onError = Color(0xFF690005),
)
