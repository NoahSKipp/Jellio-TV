package com.jellio.tv.ui.detail

// Real bug found live, four times running: neither focusProperties{down=},
// a plain onKeyEvent, nor onPreviewKeyEvent attached anywhere in
// StreamPickerOverlay's own Compose tree - down to its own outermost
// Box, the real root of that whole overlay's focus subtree - ever
// actually redirected a real Down/Up press between the Resume button/
// language chips and the stream list. Something ahead of Compose's own
// focus-based key dispatch was already swallowing it every time,
// regardless of where in that dispatch chain the handler sat.
//
// This bridges around Compose's own key dispatch entirely: MainActivity
// below overrides dispatchKeyEvent, which Android calls on every real
// key event before its own View/Compose focus system gets a look at
// it at all. StreamPickerOverlay sets these two callbacks live while
// it is actually the front-most screen and clears them on its own real
// dismiss; a plain top-level var rather than a CompositionLocal or
// ViewModel field, since nothing outside this one overlay/Activity
// pair ever needs to read it.
internal object StreamPickerDpadBridge {
    var onDpadDown: (() -> Boolean)? = null
    var onDpadUp: (() -> Boolean)? = null
}
