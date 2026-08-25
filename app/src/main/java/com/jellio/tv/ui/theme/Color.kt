package com.jellio.tv.ui.theme

import androidx.compose.ui.graphics.Color

// Mirrors css/app.css's own :root tokens in Jellio-Plugin (the web
// frontend), so this app and the web client stay one real look
// rather than two designs that happen to share a name. Keep in sync
// by hand: bump one, bump the other. Cross-checked directly against
// NuvioWeb's own css/base.css (the real source both files port these
// from): its own palette is genuinely monochrome, near-black grounds
// and #f5f5f5 as the one bright "secondary" tone, no separate accent
// hue anywhere. No color here is invented past what either real
// source actually defines.
val JellioBg = Color(0xFF0D0D0D)
val JellioBgElevated = Color(0xFF1A1A1A)
val JellioSecondary = Color(0xFFF5F5F5)
val JellioSecondaryVariant = Color(0xFFE0E0E0)
val JellioText = Color(0xFFFFFFFF)
val JellioTextSecondary = Color(0xFFB3B3B3)
val JellioTextTertiary = Color(0xFF808080)
val JellioBorder = Color(0xFF333333)
// css/app.css's own .jellio-card-options-item-danger: #ff6b81.
val JellioDanger = Color(0xFFFF6B81)
