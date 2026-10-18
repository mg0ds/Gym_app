package com.gymtracker.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Paleta aplikacji: granat, stalowy błękit, jasny błękit, kość słoniowa i
 * czerwień. Kolory systemowe (Material You) są **wyłączone** - patrz
 * `GymTrackerTheme` - bo paleta ma być rozpoznawalna na każdym urządzeniu,
 * a nie zmieniać się razem z tapetą.
 *
 * Role dobrane pod czytelność w siłowni:
 *
 * * **primary** = granat (`#1d3557`) - akcje i wyróżnienia, na nim jasny tekst,
 * * **secondary** = stalowy błękit (`#457b9d`) - drugi plan i seria na wykresach,
 * * **tertiary** = czerwień (`#e63946`) - akcent (rekordy, linie celu),
 * * **error** = przyciemniona czerwień - błąd musi być czytelny drobnym tekstem,
 * * powierzchnie = kość słoniowa (`#f1faee`) z błękitnymi wariantami.
 */
internal val GymPaletteNavy = Color(0xFF1D3557)
internal val GymPaletteSteel = Color(0xFF457B9D)
internal val GymPaletteLightBlue = Color(0xFFA8DADC)
internal val GymPaletteCream = Color(0xFFF1FAEE)
internal val GymPaletteRed = Color(0xFFE63946)

// --- jasny motyw ---

internal val GymPrimaryLight = GymPaletteNavy
internal val GymOnPrimaryLight = GymPaletteCream
internal val GymPrimaryContainerLight = GymPaletteLightBlue
internal val GymOnPrimaryContainerLight = Color(0xFF0B1D33)

internal val GymSecondaryLight = GymPaletteSteel
internal val GymOnSecondaryLight = GymPaletteCream
internal val GymSecondaryContainerLight = Color(0xFFD3E7EC)
internal val GymOnSecondaryContainerLight = Color(0xFF102A38)

internal val GymTertiaryLight = GymPaletteRed
internal val GymOnTertiaryLight = Color.White
internal val GymTertiaryContainerLight = Color(0xFFFFDADC)
internal val GymOnTertiaryContainerLight = Color(0xFF5A0C13)

internal val GymErrorLight = Color(0xFFA8202B)
internal val GymOnErrorLight = Color.White
internal val GymErrorContainerLight = Color(0xFFFFDAD8)
internal val GymOnErrorContainerLight = Color(0xFF410006)

internal val GymBackgroundLight = GymPaletteCream
internal val GymOnBackgroundLight = Color(0xFF14202E)
internal val GymSurfaceLight = GymPaletteCream
internal val GymOnSurfaceLight = Color(0xFF14202E)
internal val GymSurfaceVariantLight = Color(0xFFDCE7E8)
internal val GymOnSurfaceVariantLight = Color(0xFF41525D)

internal val GymLightColors = lightColorScheme(
    primary = GymPrimaryLight,
    onPrimary = GymOnPrimaryLight,
    primaryContainer = GymPrimaryContainerLight,
    onPrimaryContainer = GymOnPrimaryContainerLight,
    secondary = GymSecondaryLight,
    onSecondary = GymOnSecondaryLight,
    secondaryContainer = GymSecondaryContainerLight,
    onSecondaryContainer = GymOnSecondaryContainerLight,
    tertiary = GymTertiaryLight,
    onTertiary = GymOnTertiaryLight,
    tertiaryContainer = GymTertiaryContainerLight,
    onTertiaryContainer = GymOnTertiaryContainerLight,
    error = GymErrorLight,
    onError = GymOnErrorLight,
    errorContainer = GymErrorContainerLight,
    onErrorContainer = GymOnErrorContainerLight,
    background = GymBackgroundLight,
    onBackground = GymOnBackgroundLight,
    surface = GymSurfaceLight,
    onSurface = GymOnSurfaceLight,
    surfaceVariant = GymSurfaceVariantLight,
    onSurfaceVariant = GymOnSurfaceVariantLight,
    // Powierzchnie pochodne definiujemy jawnie: domyślne (szare) wpadałyby
    // środkiem ekranu między kremowe tło i granatowe napisy.
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7FBF5),
    surfaceContainer = Color(0xFFEBF3EC),
    surfaceContainerHigh = Color(0xFFE4EDE7),
    surfaceContainerHighest = Color(0xFFDDE7E1),
    surfaceDim = Color(0xFFDDE7E1),
    surfaceBright = GymPaletteCream,
    outline = Color(0xFF6C7C86),
    outlineVariant = Color(0xFFBBC9CC),
    inverseSurface = Color(0xFF2B3742),
    inverseOnSurface = Color(0xFFEDF3ED),
    inversePrimary = GymPaletteLightBlue,
    scrim = Color(0xFF000000),
)

// --- ciemny motyw ---

internal val GymPrimaryDark = GymPaletteLightBlue
internal val GymOnPrimaryDark = Color(0xFF04202C)
internal val GymPrimaryContainerDark = Color(0xFF274763)
internal val GymOnPrimaryContainerDark = Color(0xFFD2EAEC)

internal val GymSecondaryDark = Color(0xFF96BBD1)
internal val GymOnSecondaryDark = Color(0xFF0E2531)
internal val GymSecondaryContainerDark = Color(0xFF2F536A)
internal val GymOnSecondaryContainerDark = Color(0xFFCFE6F1)

internal val GymTertiaryDark = Color(0xFFFFB3B7)
internal val GymOnTertiaryDark = Color(0xFF4A0910)
internal val GymTertiaryContainerDark = Color(0xFF7E1A22)
internal val GymOnTertiaryContainerDark = Color(0xFFFFDADC)

internal val GymErrorDark = Color(0xFFFFB4AB)
internal val GymOnErrorDark = Color(0xFF690005)
internal val GymErrorContainerDark = Color(0xFF93000A)
internal val GymOnErrorContainerDark = Color(0xFFFFDAD6)

internal val GymBackgroundDark = Color(0xFF111A24)
internal val GymOnBackgroundDark = Color(0xFFE2EAE5)
internal val GymSurfaceDark = Color(0xFF111A24)
internal val GymOnSurfaceDark = Color(0xFFE2EAE5)
internal val GymSurfaceVariantDark = Color(0xFF3A4A56)
internal val GymOnSurfaceVariantDark = Color(0xFFC3D3D6)

internal val GymDarkColors = darkColorScheme(
    primary = GymPrimaryDark,
    onPrimary = GymOnPrimaryDark,
    primaryContainer = GymPrimaryContainerDark,
    onPrimaryContainer = GymOnPrimaryContainerDark,
    secondary = GymSecondaryDark,
    onSecondary = GymOnSecondaryDark,
    secondaryContainer = GymSecondaryContainerDark,
    onSecondaryContainer = GymOnSecondaryContainerDark,
    tertiary = GymTertiaryDark,
    onTertiary = GymOnTertiaryDark,
    tertiaryContainer = GymTertiaryContainerDark,
    onTertiaryContainer = GymOnTertiaryContainerDark,
    error = GymErrorDark,
    onError = GymOnErrorDark,
    errorContainer = GymErrorContainerDark,
    onErrorContainer = GymOnErrorContainerDark,
    background = GymBackgroundDark,
    onBackground = GymOnBackgroundDark,
    surface = GymSurfaceDark,
    onSurface = GymOnSurfaceDark,
    surfaceVariant = GymSurfaceVariantDark,
    onSurfaceVariant = GymOnSurfaceVariantDark,
    surfaceContainerLowest = Color(0xFF0B131C),
    surfaceContainerLow = Color(0xFF17212C),
    surfaceContainer = Color(0xFF1B2632),
    surfaceContainerHigh = Color(0xFF25313E),
    surfaceContainerHighest = Color(0xFF303C4A),
    surfaceDim = Color(0xFF111A24),
    surfaceBright = Color(0xFF37424F),
    outline = Color(0xFF8998A3),
    outlineVariant = Color(0xFF3A4A56),
    inverseSurface = Color(0xFFE2EAE5),
    inverseOnSurface = Color(0xFF25313E),
    inversePrimary = GymPaletteNavy,
    scrim = Color(0xFF000000),
)
