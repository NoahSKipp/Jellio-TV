package com.jellio.tv.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jellio.tv.data.prefs.DisplayPreferences
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary

// A full-screen 4-corner safe area calibration tool mirroring console game setup screens.
// Displays corner alignment brackets (top-left, top-right, bottom-left, bottom-right)
// and border guide lines that scale in real time as the user adjusts the margin.
@Composable
fun OverscanCalibrationModal(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var currentPercent by remember { mutableIntStateOf(DisplayPreferences.overscanPercent) }
    val initialFocus = remember { FocusRequester() }

    fun applyChange(newPercent: Int) {
        val clamped = newPercent.coerceIn(DisplayPreferences.MIN_PERCENT, DisplayPreferences.MAX_PERCENT)
        currentPercent = clamped
        DisplayPreferences.setOverscan(context, clamped)
    }

    LaunchedEffect(Unit) {
        initialFocus.requestFocus()
    }

    BackHandler {
        DisplayPreferences.setOverscan(context, currentPercent)
        onDismiss()
    }

    val config = LocalConfiguration.current
    val insetX = (config.screenWidthDp * (currentPercent / 100f) / 2f).dp
    val insetY = (config.screenHeightDp * (currentPercent / 100f) / 2f).dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusProperties { onExit = { FocusRequester.Cancel } },
    ) {
        // Outer reference bounds with 4-corner brackets
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = insetX, vertical = insetY),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 3.dp.toPx()
                val armLength = 48.dp.toPx()
                val color = Color.White

                // Connecting guide rectangle outline
                drawRect(
                    color = color.copy(alpha = 0.45f),
                    style = Stroke(width = 1.5.dp.toPx()),
                )

                // Top-Left corner bracket
                drawPath(
                    path = Path().apply {
                        moveTo(0f, armLength)
                        lineTo(0f, 0f)
                        lineTo(armLength, 0f)
                    },
                    color = color,
                    style = Stroke(width = strokeWidth),
                )
                // Top-Left corner arrow
                drawLine(
                    color = color,
                    start = Offset(armLength * 0.4f, armLength * 0.4f),
                    end = Offset(4f, 4f),
                    strokeWidth = strokeWidth,
                )

                // Top-Right corner bracket
                drawPath(
                    path = Path().apply {
                        moveTo(size.width - armLength, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width, armLength)
                    },
                    color = color,
                    style = Stroke(width = strokeWidth),
                )
                // Top-Right corner arrow
                drawLine(
                    color = color,
                    start = Offset(size.width - armLength * 0.4f, armLength * 0.4f),
                    end = Offset(size.width - 4f, 4f),
                    strokeWidth = strokeWidth,
                )

                // Bottom-Left corner bracket
                drawPath(
                    path = Path().apply {
                        moveTo(0f, size.height - armLength)
                        lineTo(0f, size.height)
                        lineTo(armLength, size.height)
                    },
                    color = color,
                    style = Stroke(width = strokeWidth),
                )
                // Bottom-Left corner arrow
                drawLine(
                    color = color,
                    start = Offset(armLength * 0.4f, size.height - armLength * 0.4f),
                    end = Offset(4f, size.height - 4f),
                    strokeWidth = strokeWidth,
                )

                // Bottom-Right corner bracket
                drawPath(
                    path = Path().apply {
                        moveTo(size.width - armLength, size.height)
                        lineTo(size.width, size.height)
                        lineTo(size.width, size.height - armLength)
                    },
                    color = color,
                    style = Stroke(width = strokeWidth),
                )
                // Bottom-Right corner arrow
                drawLine(
                    color = color,
                    start = Offset(size.width - armLength * 0.4f, size.height - armLength * 0.4f),
                    end = Offset(size.width - 4f, size.height - 4f),
                    strokeWidth = strokeWidth,
                )
            }
        }

        // Center control panel
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 560.dp)
                .background(JellioBgElevated.copy(alpha = 0.95f), RoundedCornerShape(20.dp))
                .padding(32.dp),
        ) {
            Text(
                text = "Display Safe Area (Overscan)",
                style = MaterialTheme.typography.titleLarge,
                color = JellioText,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Adjust using Left / Right until all four white corner brackets and outer borders fit completely inside your TV screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = JellioTextSecondary,
                textAlign = TextAlign.Center,
            )

            // Current percentage readout
            Text(
                text = if (currentPercent == 0) "0% (Full screen)" else "Inset: $currentPercent%",
                style = MaterialTheme.typography.headlineMedium,
                color = JellioSecondary,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            // D-Pad adjustment buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = { applyChange(currentPercent - 1) },
                    enabled = currentPercent > DisplayPreferences.MIN_PERCENT,
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(999.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = JellioBg,
                        contentColor = JellioText,
                        focusedContainerColor = Color.White.copy(alpha = 0.35f),
                        focusedContentColor = JellioText,
                    ),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(border = BorderStroke(2.5.dp, Color.White), shape = RoundedCornerShape(999.dp)),
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    modifier = Modifier.onKeyEvent {
                        if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionLeft && currentPercent > DisplayPreferences.MIN_PERCENT) {
                            applyChange(currentPercent - 1)
                            true
                        } else false
                    },
                ) {
                    Text(
                        text = "◀ Expand (-)",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }

                Surface(
                    onClick = { applyChange(currentPercent + 1) },
                    enabled = currentPercent < DisplayPreferences.MAX_PERCENT,
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(999.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = JellioBg,
                        contentColor = JellioText,
                        focusedContainerColor = Color.White.copy(alpha = 0.35f),
                        focusedContentColor = JellioText,
                    ),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(border = BorderStroke(2.5.dp, Color.White), shape = RoundedCornerShape(999.dp)),
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    modifier = Modifier
                        .focusRequester(initialFocus)
                        .onKeyEvent {
                            if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionRight && currentPercent < DisplayPreferences.MAX_PERCENT) {
                                applyChange(currentPercent + 1)
                                true
                            } else false
                        },
                ) {
                    Text(
                        text = "Inset (+) ▶",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            }

            // Action row: Reset and Save
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                if (currentPercent > 0) {
                    Surface(
                        onClick = { applyChange(0) },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(999.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.12f),
                            contentColor = JellioTextSecondary,
                            focusedContainerColor = Color.White.copy(alpha = 0.25f),
                            focusedContentColor = JellioText,
                        ),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(border = BorderStroke(2.5.dp, Color.White), shape = RoundedCornerShape(999.dp)),
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    ) {
                        Text(
                            text = "Reset (0%)",
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        )
                    }
                }

                Surface(
                    onClick = {
                        DisplayPreferences.setOverscan(context, currentPercent)
                        onDismiss()
                    },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(999.dp)),
                    colors = com.jellio.tv.ui.common.primaryButtonColors(),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(border = BorderStroke(2.5.dp, Color.White), shape = RoundedCornerShape(999.dp)),
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                ) {
                    Text(
                        text = "Save & Apply",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}
