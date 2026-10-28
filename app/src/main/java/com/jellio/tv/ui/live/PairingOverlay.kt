package com.jellio.tv.ui.live

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.jellio.tv.ui.common.ScreenSpinner
import com.jellio.tv.ui.common.primaryButtonColors
import com.jellio.tv.ui.i18n.Strings
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary

internal fun encodeQr(text: String): BitMatrix? = runCatching {
    QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2))
}.getOrNull()

@Composable
private fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) { encodeQr(text) } ?: return
    Canvas(modifier = modifier.background(Color.White)) {
        val cell = size.minDimension / matrix.width
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix.get(x, y)) {
                    // A hair of overlap so no seams show between cells.
                    drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
                }
            }
        }
    }
}

// The phone side is the plugin's /Jellio/add page: no sign-in there, the
// code stands in for it.
@Composable
internal fun PairingOverlay(pairing: LivePairing, onNewCode: () -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val focus = remember { FocusRequester() }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)), contentAlignment = Alignment.Center) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(JellioBgElevated).padding(40.dp),
        ) {
            Box(modifier = Modifier.size(280.dp).clip(RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                when {
                    pairing.code.isEmpty() && !pairing.expired -> ScreenSpinner()
                    !pairing.expired -> QrCode(pairing.url, Modifier.size(280.dp))
                    else -> Box(Modifier.size(280.dp).background(Color.White.copy(alpha = 0.06f)))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.width(460.dp)) {
                Text(text = Strings.get("live_link_qr_title"), style = MaterialTheme.typography.headlineSmall, color = JellioText)
                Text(text = Strings.get("live_link_qr_scan"), color = JellioTextSecondary)
                if (pairing.code.isNotEmpty() && !pairing.expired) {
                    val address = pairing.url.substringBefore("?")
                    Text(text = Strings.format("live_link_qr_manual", address, pairing.code), color = JellioTextSecondary)
                    Text(
                        text = pairing.code,
                        color = JellioText,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 36.sp,
                        letterSpacing = 6.sp,
                    )
                }
                pairing.addedName?.let {
                    Text(text = Strings.format("live_link_qr_added", it), color = Color(0xFF66BB6A), fontWeight = FontWeight.SemiBold)
                }
                if (pairing.expired) {
                    Text(text = Strings.get("live_link_qr_expired"), color = JellioTextSecondary)
                    Surface(
                        onClick = onNewCode,
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                        colors = primaryButtonColors(),
                        modifier = Modifier.focusRequester(focus),
                    ) {
                        Text(text = Strings.get("live_link_qr_new"), modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    }
                } else {
                    Surface(
                        onClick = onClose,
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                        colors = primaryButtonColors(),
                        modifier = Modifier.focusRequester(focus),
                    ) {
                        Text(text = Strings.get("action_done"), modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    }
                }
            }
        }
    }
    LaunchedEffect(pairing.expired, pairing.code.isEmpty()) { runCatching { focus.requestFocus() } }
}
