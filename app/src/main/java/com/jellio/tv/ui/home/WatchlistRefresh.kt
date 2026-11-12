package com.jellio.tv.ui.home

// A row is redrawn only when its titles or their order changed.
internal fun watchlistIdsChanged(shown: List<String>, fresh: List<String>): Boolean = shown != fresh
