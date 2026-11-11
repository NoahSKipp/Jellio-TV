package com.jellio.tv.ui.nowplaying

const val PILL_WEIGHT_COMPACT = 1f
const val PILL_WEIGHT_EXPANDED = 2.2f

// Only the selected or focused tab shows its label; the others are icon + badge.
fun quickHubPillExpanded(selected: Boolean, focused: Boolean): Boolean = selected || focused

fun quickHubPillWeight(selected: Boolean, focused: Boolean): Float =
    if (quickHubPillExpanded(selected, focused)) PILL_WEIGHT_EXPANDED else PILL_WEIGHT_COMPACT
