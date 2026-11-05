package com.gymtracker.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Paleta „Obsidian & Citrus”: atramentowa zieleń, głęboki mech, porcelanowe tło
 * i jeden elektryczny akcent limonkowy. Kolory systemowe (Material You) są
 * **wyłączone** - patrz `GymTrackerTheme` - bo paleta ma być rozpoznawalna na
 * każdym urządzeniu, a nie zmieniać się razem z tapetą.
 *
 * Role dobrane pod czytelność w siłowni:
 *
 * * **primary** = atramentowa zieleń (jasny motyw) - akcje i główne serie,
 * * **secondary** = głęboki mech - drugi plan i najlżejsza seria rampy,
 * * **tertiary** = palona miedź - rekordy, szacowany 1RM i ostrzeżenia,
 * * **error** = ceglasta czerwień - błąd musi być czytelny drobnym tekstem,
 * * powierzchnie = porcelana i kość słoniowa z chłodnym, zielonym cieniem.
 *
 * Limonka ([GymLime]) nie jest rolą Material: używamy jej świadomie na ciemnych
 * kartach-herbach (kalendarz, zegar treningu), gdzie ma być mocnym akcentem,
 * a nie kolorem całego interfejsu.
 */
internal val GymLime = Color(0xFFC8F65A)
internal val GymHeroInk = Color(0xFF0D1510)
internal val GymHeroMoss = Color(0xFF1F3B2A)
internal val GymHeroText = Color(0xFFF4F7ED)

// --- jasny motyw ---

internal val GymPrimaryLight = Color(0xFF101813)
internal val GymOnPrimaryLight = Color(0xFFF5F8EE)
internal val GymPrimaryContainerLight = Color(0xFFDCE9C6)
internal val GymOnPrimaryContainerLight = Color(0xFF19250E)

internal val GymSecondaryLight = Color(0xFF3F5D4E)
internal val GymOnSecondaryLight = Color(0xFFF5F8EE)
internal val GymSecondaryContainerLight = Color(0xFFD4E3D9)
internal val GymOnSecondaryContainerLight = Color(0xFF0D2118)

internal val GymTertiaryLight = Color(0xFF9A5B2C)
internal val GymOnTertiaryLight = Color(0xFFFFF8EF)
internal val GymTertiaryContainerLight = Color(0xFFFFE0BE)
internal val GymOnTertiaryContainerLight = Color(0xFF2F1800)

internal val GymErrorLight = Color(0xFFAD2A1F)
internal val GymOnErrorLight = Color.White
internal val GymErrorContainerLight = Color(0xFFFFDAD4)
internal val GymOnErrorContainerLight = Color(0xFF410000)

internal val GymBackgroundLight = Color(0xFFF5F2EA)
internal val GymOnBackgroundLight = Color(0xFF171C17)
internal val GymSurfaceLight = Color(0xFFFBF9F3)
internal val GymOnSurfaceLight = Color(0xFF171C17)
internal val GymSurfaceVariantLight = Color(0xFFE4E1D6)
internal val GymOnSurfaceVariantLight = Color(0xFF4C544A)

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
    // środkiem ekranu między porcelanowe tło i atramentowe napisy.
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCFAF5),
    surfaceContainer = Color(0xFFF1EEE5),
    surfaceContainerHigh = Color(0xFFEAE6DA),
    surfaceContainerHighest = Color(0xFFE2DED1),
    surfaceDim = Color(0xFFE4E0D4),
    surfaceBright = Color(0xFFFCFAF5),
    outline = Color(0xFF777E73),
    outlineVariant = Color(0xFFC7CBBE),
    inverseSurface = Color(0xFF2A322B),
    inverseOnSurface = Color(0xFFEDF2E8),
    inversePrimary = Color(0xFFC8F65A),
    scrim = Color(0xFF000000),
)

// --- ciemny motyw ---

internal val GymPrimaryDark = Color(0xFFD8F6A4)
internal val GymOnPrimaryDark = Color(0xFF1A260A)
internal val GymPrimaryContainerDark = Color(0xFF33421A)
internal val GymOnPrimaryContainerDark = Color(0xFFE7F7C6)

internal val GymSecondaryDark = Color(0xFFA9C8B8)
internal val GymOnSecondaryDark = Color(0xFF10251A)
internal val GymSecondaryContainerDark = Color(0xFF2A4436)
internal val GymOnSecondaryContainerDark = Color(0xFFCFE8D9)

internal val GymTertiaryDark = Color(0xFFF0B57A)
internal val GymOnTertiaryDark = Color(0xFF3B2000)
internal val GymTertiaryContainerDark = Color(0xFF5A3810)
internal val GymOnTertiaryContainerDark = Color(0xFFFFE0BE)

internal val GymErrorDark = Color(0xFFFFB4A8)
internal val GymOnErrorDark = Color(0xFF690000)
internal val GymErrorContainerDark = Color(0xFF93000A)
internal val GymOnErrorContainerDark = Color(0xFFFFDAD4)

internal val GymBackgroundDark = Color(0xFF0B100C)
internal val GymOnBackgroundDark = Color(0xFFE6EDE2)
internal val GymSurfaceDark = Color(0xFF101610)
internal val GymOnSurfaceDark = Color(0xFFE6EDE2)
internal val GymSurfaceVariantDark = Color(0xFF3B463A)
internal val GymOnSurfaceVariantDark = Color(0xFFC2CCBE)

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
    surfaceContainerLowest = Color(0xFF070B08),
    surfaceContainerLow = Color(0xFF141C15),
    surfaceContainer = Color(0xFF18211A),
    surfaceContainerHigh = Color(0xFF222C23),
    surfaceContainerHighest = Color(0xFF2D382E),
    surfaceDim = Color(0xFF0B100C),
    surfaceBright = Color(0xFF37423A),
    outline = Color(0xFF8C9688),
    outlineVariant = Color(0xFF3C463B),
    inverseSurface = Color(0xFFE6EDE2),
    inverseOnSurface = Color(0xFF222C23),
    inversePrimary = Color(0xFF101813),
    scrim = Color(0xFF000000),
)
