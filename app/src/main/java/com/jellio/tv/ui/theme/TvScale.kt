package com.jellio.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp

// Real 10-foot layout's own fixed rem sizing was tuned against one
// real reference viewport, never a range: this app's own card/hero
// dimensions were the exact same fixed dp values regardless of what
// TV they actually landed on, fine on the exact box this was built
// against but visibly too small on a lower-density box reporting a
// narrower dp width (a smaller effective canvas the same fixed sizes
// now eat proportionally more of), or too cramped on a wider one.
// screenWidthDp is already density-independent, so this is real
// leftover screen-shape variance, not a resolution problem density
// buckets already solve on their own.
private val ReferenceScreenWidthDp = 960

// Clamped rather than left open-ended: a reader on a genuinely tiny
// or ultrawide panel should still see recognisably the same layout,
// not cards shrunk to illegible or blown up past what a row can still
// fit several of.
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
