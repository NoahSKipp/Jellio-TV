package com.jellio.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

// Only the real tokens css/app.css itself defines get an override
// here; everything else keeps tv-material3's own dark scheme default
// rather than inventing a value the web build never had an opinion
// on.
private val JellioColorScheme = darkColorScheme(
    background = JellioBg,
    onBackground = JellioText,
    surface = JellioBgElevated,
    onSurface = JellioText,
    surfaceVariant = JellioBgElevated,
    onSurfaceVariant = JellioTextSecondary,
    primary = JellioSecondary,
    onPrimary = JellioBg,
    // Real feedback checked against NuvioWeb's own css/base.css: no
    // separate accent hue exists there at all, every bright tone in
    // its own real palette traces back to the one #f5f5f5/#e0e0e0
    // pair. secondary was bound to an invented orange with no real
    // source on either side; the variant tone that pair's own second
    // half already is fits this role without introducing a color
    // neither real source ever had an opinion on.
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
