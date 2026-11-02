package com.jellio.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
    MaterialTheme(
        colorScheme = JellioColorScheme,
        typography = JellioTypography,
    ) {
        CompositionLocalProvider(LocalTvScale provides rememberTvScale(), content = content)
    }
}
