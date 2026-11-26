package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val CallGuardDarkColorScheme = darkColorScheme(
    primary = CobaltPrimaryDark,
    onPrimary = CobaltOnPrimaryDark,
    primaryContainer = CobaltPrimaryContainerDark,
    onPrimaryContainer = CobaltOnPrimaryContainerDark,
    secondary = TealSecondaryDark,
    onSecondary = TealOnSecondaryDark,
    secondaryContainer = TealSecondaryContainerDark,
    onSecondaryContainer = TealOnSecondaryContainerDark,
    tertiary = SlateTertiaryDark,
    onTertiary = SlateOnTertiaryDark,
    tertiaryContainer = SlateTertiaryContainerDark,
    onTertiaryContainer = SlateOnTertiaryContainerDark,
    error = CrimsonErrorDark,
    onError = CrimsonOnErrorDark,
    errorContainer = CrimsonErrorContainerDark,
    onErrorContainer = CrimsonOnErrorContainerDark,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant
)

private val CallGuardLightColorScheme = lightColorScheme(
    primary = CobaltPrimary,
    onPrimary = CobaltOnPrimary,
    primaryContainer = CobaltPrimaryContainer,
    onPrimaryContainer = CobaltOnPrimaryContainer,
    secondary = TealSecondary,
    onSecondary = TealOnSecondary,
    secondaryContainer = TealSecondaryContainer,
    onSecondaryContainer = TealOnSecondaryContainer,
    tertiary = SlateTertiary,
    onTertiary = SlateOnTertiary,
    tertiaryContainer = SlateTertiaryContainer,
    onTertiaryContainer = SlateOnTertiaryContainer,
    error = CrimsonError,
    onError = CrimsonOnError,
    errorContainer = CrimsonErrorContainer,
    onErrorContainer = CrimsonOnErrorContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant
)

/**
 * Wallpaper-harmonized deep atmospheric tones used by dark hero surfaces (Shield banner &
 * full-screen In-Call UI) so they adapt smoothly to Material You dynamic colors while
 * guaranteeing high contrast for white foreground content.
 */
@Immutable
data class CallGuardHeroColors(
    val deepSurface: Color = BannerNavyDeep,
    val midSurface: Color = BannerNavyMid,
    val bottomSurface: Color = Color(0xFF071322)
)

val LocalCallGuardHeroColors = staticCompositionLocalOf { CallGuardHeroColors() }

/**
 * Root Material 3 theme for CallGuard.
 *
 * Uses Android 12+ (API 31+) Material You wallpaper-based dynamic colors by default
 * ([dynamicLightColorScheme] / [dynamicDarkColorScheme]), with a curated cobalt security
 * fallback palette on Android 11 and below.
 */
@Composable
fun CallGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val supportsDynamicColor = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        supportsDynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        supportsDynamicColor && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> CallGuardDarkColorScheme
        else -> CallGuardLightColorScheme
    }

    // Derive deep atmospheric hero tones from the active (dynamic or fallback) color scheme
    // so dark banners and the full-screen call screen echo the user's wallpaper hue.
    val heroColors = if (supportsDynamicColor) {
        val darkDynamic = dynamicDarkColorScheme(context)
        CallGuardHeroColors(
            deepSurface = lerp(darkDynamic.background, darkDynamic.primaryContainer, 0.34f),
            midSurface = lerp(darkDynamic.surface, darkDynamic.primary, 0.22f),
            bottomSurface = lerp(darkDynamic.background, Color.Black, 0.35f)
        )
    } else {
        CallGuardHeroColors()
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalCallGuardHeroColors provides heroColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CallGuardTypography,
            content = content
        )
    }
}
