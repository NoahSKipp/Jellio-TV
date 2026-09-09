package com.jellio.tv.ui.detail

// Down's own real bug turned out to live one layer lower than any of
// this: firstSourceCardFocusRequester.requestFocus() (StreamPicker-
// Overlay.kt) was never actually landing focus on a LazyColumn item at
// all, confirmed live via Logcat - the same gap ui/home/HomeScreen.kt's
// own header already documents, fixed there with focusRestorer() and
// now fixed the same way here. This bridge stays for Up only: leaving
// the stream list back up to the Resume button/language chips still
// needs an explicit redirect (Compose's own default arrow-key search
// has no reason to prefer that direction on its own), and
// initialFocusRequester targets a plain non-lazy Surface, which
// requestFocus() reaches reliably.
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
