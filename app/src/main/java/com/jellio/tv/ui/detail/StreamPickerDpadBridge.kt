package com.jellio.tv.ui.detail

// Real bug traced live over several rounds: nothing about how Down/Up
// were being requested was ever actually broken - focusProperties,
// onKeyEvent, onPreviewKeyEvent, Compose's own default arrow-key
// search, a plain declarative focusRestorer() entry point, all
// eliminated in turn. The real cause was LazyColumn's own item
// recycling silently breaking requestFocus() into its items entirely -
// a direct, unambiguous requestFocus() call with no key event involved
// at all produced zero effect, confirmed via Logcat, matching a
// documented real upstream Compose bug (JetBrains/compose-multiplatform
// #3526's own real finding: "using a Column instead of LazyColumn
// works as expected"). StreamPickerOverlay's own stream list is a
// plain Column now instead, so requestFocus() into it works the same
// reliable way it already does for every other Surface in that
// overlay (Resume/chips/Retry) - this bridge just carries that call
// through dispatchKeyEvent, same as it always did.
//
// MainActivity overrides dispatchKeyEvent, which Android calls on
// every real key event before its own View/Compose focus system gets
// a look at it at all. StreamPickerOverlay sets these callbacks live
// while it is actually the front-most screen and clears them on its
// own real dismiss; a plain top-level var rather than a
// CompositionLocal or ViewModel field, since nothing outside this one
// overlay/Activity pair ever needs to read it.
internal object StreamPickerDpadBridge {
    var onDpadDown: (() -> Boolean)? = null
    var onDpadUp: (() -> Boolean)? = null
}
