package com.jellio.tv.ui.home

import androidx.lifecycle.ViewModel
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.model.BaseItemDto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.session.SessionManager
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

val LocalGelatoPrefetch = androidx.compose.runtime.compositionLocalOf<(BaseItemDto) -> Unit> { {} }

@HiltViewModel
class GelatoPrefetchViewModel @Inject constructor(
    private val repository: JellioRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {
    private val seriesTargetCache = ConcurrentHashMap<String, String>()

    fun prefetch(item: BaseItemDto) {
        repository.seedPreviews(listOf(item))
        if (item.Type != "Movie" && item.Type != "Episode" && item.Type != "Series") return
        repository.preloadDetails(item.Id)
        if (item.Type == "Series") {
            val cachedEpId = seriesTargetCache[item.Id]
            if (cachedEpId != null) {
                repository.prefetchStreams(cachedEpId)
            } else {
                viewModelScope.launch {
                    val session = sessionManager.sessionFlow.firstOrNull()
                    if (session != null) {
                        val target = com.jellio.tv.data.catching {
                            repository.resolveSeriesPlayTarget(item.Id, session.userId)
                        }.getOrNull()
                        val epId = target?.episode?.Id
                        if (epId != null) {
                            seriesTargetCache[item.Id] = epId
                            repository.preloadDetails(epId)
                            repository.prefetchStreams(epId)
                        } else {
                            repository.prefetchStreams(item.Id)
                        }
                    } else {
                        repository.prefetchStreams(item.Id)
                    }
                }
            }
        } else {
            repository.prefetchStreams(item.Id)
        }
    }
}
