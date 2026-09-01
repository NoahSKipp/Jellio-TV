package com.jellio.tv.ui.home

import androidx.lifecycle.ViewModel
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.model.BaseItemDto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

val LocalGelatoPrefetch = androidx.compose.runtime.compositionLocalOf<(BaseItemDto) -> Unit> { {} }

@HiltViewModel
class GelatoPrefetchViewModel @Inject constructor(
    private val repository: JellioRepository,
) : ViewModel() {
    fun prefetch(item: BaseItemDto) {
        repository.seedPreviews(listOf(item))
        if (item.Type != "Movie" && item.Type != "Episode" && item.Type != "Series") return
        repository.preloadDetails(item.Id)
        repository.prefetchStreams(item.Id)
    }
}
