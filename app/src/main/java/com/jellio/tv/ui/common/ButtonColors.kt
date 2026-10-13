package com.jellio.tv.ui.common

import androidx.compose.runtime.Composable
import androidx.tv.material3.ClickableSurfaceColors
import androidx.tv.material3.ClickableSurfaceDefaults
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioDanger
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioTextSecondary

// A main action. At rest it's a dark pill with light text, so it never
// reads as the focused button; the solid fill only comes with focus.
@Composable
fun primaryButtonColors(): ClickableSurfaceColors = ClickableSurfaceDefaults.colors(
    containerColor = JellioBgElevated,
    contentColor = JellioSecondary,
    focusedContainerColor = JellioSecondary,
    focusedContentColor = JellioBg,
    pressedContainerColor = JellioSecondary,
    pressedContentColor = JellioBg,
    disabledContainerColor = JellioBgElevated,
    disabledContentColor = JellioTextSecondary,
)

// Sign out, log out: the same, in red.
@Composable
fun dangerButtonColors(): ClickableSurfaceColors = ClickableSurfaceDefaults.colors(
    containerColor = JellioBgElevated,
    contentColor = JellioDanger,
    focusedContainerColor = JellioDanger,
    focusedContentColor = JellioBg,
    pressedContainerColor = JellioDanger,
    pressedContentColor = JellioBg,
    disabledContainerColor = JellioBgElevated,
    disabledContentColor = JellioTextSecondary,
)
