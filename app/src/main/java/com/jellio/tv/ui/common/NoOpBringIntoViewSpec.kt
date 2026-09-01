package com.jellio.tv.ui.common

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec

@OptIn(ExperimentalFoundationApi::class)
internal object NoOpBringIntoViewSpec : BringIntoViewSpec {
    override val scrollAnimationSpec: AnimationSpec<Float> = spring()
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}
