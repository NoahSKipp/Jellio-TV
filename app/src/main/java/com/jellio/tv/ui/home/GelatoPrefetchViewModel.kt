package com.jellio.tv.ui.home

import androidx.lifecycle.ViewModel
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.model.BaseItemDto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

// Thin bridge so PosterCard/LandscapeCard (plain, reused Composables
// with no ViewModel of their own, unlike every real screen) can reach
// JellioRepository's own prefetchStreams() from an onFocusChanged
// callback without every one of their five call sites (Home/Library/
// Watchlist/Service rows, Search's own grid) needing to thread a
// prefetch lambda of its own through. hiltViewModel() below resolves
// against whichever screen's own ViewModelStoreOwner is nearest, same
// as every other real hiltViewModel() call in this app; the real work
// (dedup, its own longer-lived scope) already lives in the repository
// itself, this only exists to get a non-suspend call site into a
// Composable's own onFocusChanged.
@HiltViewModel
class GelatoPrefetchViewModel @Inject constructor(
    private val repository: JellioRepository,
) : ViewModel() {
    fun prefetch(item: BaseItemDto) {
        if (item.Type != "Movie" && item.Type != "Episode") return
        repository.prefetchStreams(item.Id)
    }
}
