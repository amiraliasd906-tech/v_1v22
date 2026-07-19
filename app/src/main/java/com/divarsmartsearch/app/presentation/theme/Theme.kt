package com.divarsmartsearch.app.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = AccentGoldLight,
    onPrimary = OnAccentGoldLight,
    primaryContainer = AccentGoldContainerLight,
    onPrimaryContainer = OnAccentGoldContainerLight,
    secondary = NeutralAccentLight,
    onSecondary = OnNeutralAccentLight,
    secondaryContainer = NeutralAccentContainerLight,
    onSecondaryContainer = OnNeutralAccentContainerLight,
    tertiary = AccentGoldLight,
    onTertiary = OnAccentGoldLight,
    tertiaryContainer = AccentGoldContainerLight,
    onTertiaryContainer = OnAccentGoldContainerLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    background = BackgroundLight,
    onBackground = OnSurfaceLight,
    outline = OutlineLight,
    error = ErrorLight,
)

private val DarkColors = darkColorScheme(
    primary = AccentGoldDark,
    onPrimary = OnAccentGoldDark,
    primaryContainer = AccentGoldContainerDark,
    onPrimaryContainer = OnAccentGoldContainerDark,
    secondary = NeutralAccentDark,
    onSecondary = OnNeutralAccentDark,
    secondaryContainer = NeutralAccentContainerDark,
    onSecondaryContainer = OnNeutralAccentContainerDark,
    tertiary = AccentGoldDark,
    onTertiary = OnAccentGoldDark,
    tertiaryContainer = AccentGoldContainerDark,
    onTertiaryContainer = OnAccentGoldContainerDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    outline = OutlineDark,
    error = ErrorDark,
)

/**
 * @param darkTheme when null, follows the system setting; when non-null,
 * this overrides it (used to honor the in-app "حالت شب" toggle in Settings).
 * Dark is the intended "hero" look for this palette (navy/charcoal with a
 * single amber-gold accent) — Light is a faithful, same-accent companion
 * for whenever the person prefers it, not an afterthought.
 */
@Composable
fun DivarSmartSearchTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val useDark = darkTheme ?: isSystemInDarkTheme()
    val colorScheme = if (useDark) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
