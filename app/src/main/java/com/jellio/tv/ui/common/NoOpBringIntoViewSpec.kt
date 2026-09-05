package com.jellio.tv.ui.common

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec

// Compose's own default per-child "bring the newly focused thing into
// view" behavior is dispatched by the focus system straight to the
// nearest scrollable ancestor's own BringIntoViewSpec, not through any
// callback this app's own code gets a chance to answer first. HomeScreen/
// LibraryScreen's own hero/coverflow item already forces an exact
// scrollToItem(0) itself on entry; wrapping that item's content in
// LocalBringIntoViewSpec.provides(this) stops the default request from
// ever nudging the list at all while focus moves between the arrows and
// View Details, rather than racing it after the fact (this file's own
// callers document several earlier rounds racing that default request
// with a delay instead, still visibly jumping for the delay's own real
// duration before snapping back).
@OptIn(ExperimentalFoundationApi::class)
internal object NoOpBringIntoViewSpec : BringIntoViewSpec {
    override val scrollAnimationSpec: AnimationSpec<Float> = spring()
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}
