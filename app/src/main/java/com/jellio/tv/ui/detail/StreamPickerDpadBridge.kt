package com.jellio.tv.ui.detail

// Down's own real bug turned out to live one layer lower than any
// navigation-layer theory: firstSourceCardFocusRequester.requestFocus()
// (StreamPickerOverlay.kt) was never actually landing focus on a
// LazyColumn item at all, confirmed live via Logcat across several
// separate rounds - from this bridge, from a real Compose
// LaunchedEffect, retried across real frames, even paired with an
// explicit scroll settle. The one thing never actually tried across
// any of that: a real stable key per item. Same real gap ui/home/
// HomeScreen.kt's own header already documents and already fixes:
// plain Compose Foundation LazyColumn advertises no default D-pad
// entry point of its own for a system that has never focused anything
// inside it yet, and without a real per-item key its own item
// recycling can silently break a remembered FocusRequester's own
// association with whichever node it was last attached to (the
// documented real upstream class this matches, JetBrains/
// compose-multiplatform#3526). focusRestorer() plus a real key fixes
// both halves at once, the same proven-working shape HomeScreen.kt
// already ships.
//
// This bridge stays for Up only: leaving the stream list back up to
// the Resume button/language chips still needs an explicit redirect
// (Compose's own default arrow-key search has no reason to prefer
// that direction on its own), and initialFocusRequester targets a
// plain non-lazy Surface, which requestFocus() reaches reliably.
//
// MainActivity overrides dispatchKeyEvent, which Android calls on
// every real key event before its own View/Compose focus system gets
// a look at it at all. StreamPickerOverlay sets this callback live
// while it is actually the front-most screen and clears it on its own
// real dismiss; a plain top-level var rather than a CompositionLocal
// or ViewModel field, since nothing outside this one overlay/Activity
// pair ever needs to read it.
internal object StreamPickerDpadBridge {
    var onDpadUp: (() -> Boolean)? = null
}
