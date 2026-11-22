package com.jellio.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val JellioColorScheme = darkColorScheme(
    background = JellioBg,
    onBackground = JellioText,
    surface = JellioBgElevated,
    onSurface = JellioText,
    surfaceVariant = JellioBgElevated,
    onSurfaceVariant = JellioTextSecondary,
    primary = JellioSecondary,
    onPrimary = JellioBg,
    secondary = JellioSecondaryVariant,
    onSecondary = JellioBg,
    border = JellioBorder,
    borderVariant = JellioBorder,
)

@Composable
fun JellioTvTheme(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val interfaceScale = com.jellio.tv.data.prefs.DisplayPreferences.interfaceScalePercent / 100f
    MaterialTheme(
        colorScheme = JellioColorScheme,
        typography = JellioTypography,
    ) {
        CompositionLocalProvider(
            LocalDensity provides Density(
                density = density.density * interfaceScale,
                fontScale = density.fontScale,
            ),
            LocalTvScale provides rememberTvScale(),
            content = content,
        )
    }
}
