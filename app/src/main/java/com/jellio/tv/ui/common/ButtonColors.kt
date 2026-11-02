package com.jellio.tv.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ClickableSurfaceColors
import androidx.tv.material3.ClickableSurfaceDefaults
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioDanger
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioTextSecondary

@Composable
fun primaryButtonColors(): ClickableSurfaceColors = ClickableSurfaceDefaults.colors(
    containerColor = Color.White.copy(alpha = 0.1f),
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
    containerColor = JellioDanger.copy(alpha = 0.16f),
    contentColor = JellioDanger,
    focusedContainerColor = JellioDanger,
    focusedContentColor = JellioBg,
    pressedContainerColor = JellioDanger,
    pressedContentColor = JellioBg,
    disabledContainerColor = JellioBgElevated,
    disabledContentColor = JellioTextSecondary,
)
