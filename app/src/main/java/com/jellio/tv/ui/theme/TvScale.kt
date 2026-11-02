package com.jellio.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp

private val ReferenceScreenWidthDp = 960

private val MinTvScale = 0.82f
private val MaxTvScale = 1.3f

val LocalTvScale = compositionLocalOf { 1f }

@Composable
fun rememberTvScale(): Float {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    return (screenWidthDp.toFloat() / ReferenceScreenWidthDp).coerceIn(MinTvScale, MaxTvScale)
}

@Composable
fun Dp.scaled(): Dp = this * LocalTvScale.current
