package com.jellio.tv.ui.nowplaying

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import com.jellio.tv.ui.theme.JellioText

// Shared by the Now Playing and Quick Hub pills so the two stay identical.
object HomePillStyle {
    val shape = RoundedCornerShape(24.dp)

    @Composable
    fun scale() = ClickableSurfaceDefaults.scale(
        focusedScale = 1.04f,
        pressedScale = 0.97f,
    )

    @Composable
    fun colors() = ClickableSurfaceDefaults.colors(
        containerColor = Color.Black.copy(alpha = 0.70f),
        focusedContainerColor = Color.White.copy(alpha = 0.22f),
        contentColor = JellioText,
        focusedContentColor = Color.White,
    )

    @Composable
    fun border() = ClickableSurfaceDefaults.border(
        border = Border(
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            shape = shape,
        ),
        focusedBorder = Border(
            border = BorderStroke(2.dp, Color.White),
            shape = shape,
        ),
    )
}
