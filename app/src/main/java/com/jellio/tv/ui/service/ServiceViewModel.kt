package com.jellio.tv.ui.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.groupByService
import com.jellio.tv.data.model.rowTitle
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.home.HomeSection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SERVICE_ROW_LIMIT = 24

data class ServiceUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val serviceName: String = "",
    val heroItem: BaseItemDto? = null,
    val sections: List<HomeSection> = emptyList(),
)

// Real port of screens/service.js: every real catalog collection
// matched to this service, one row per collection. A reduced pass,
// same real discipline LibraryScreen's own first pass took before its
// own coverflow carousel followed: this app's own real hero, matched
// collections, dedupe against nothing (a service page is its own
// browse destination, not sharing home's own exclude set), not yet
// that file's own genre/type filter chips, a real further pass of its
// own worth building out properly rather than guessed at here.
@HiltViewModel
class ServiceViewModel @Inject constructor(
    private val repository: JellioRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServiceUiState())
    val uiState: StateFlow<ServiceUiState> = _uiState.asStateFlow()

    private var loadedService: String? = null

    fun load(session: Session, serviceName: String) {
        if (loadedService == serviceName) return
        loadedService = serviceName
        viewModelScope.launch {
            _uiState.value = ServiceUiState(isLoading = true, serviceName = serviceName)
            try {
                val collections = repository.getCollections(session.userId)
                val matching = groupByService(collections)[serviceName].orEmpty()

                val sections = mutableListOf<HomeSection>()
                var heroItem: BaseItemDto? = null
                var heroRating = -1.0

                matching.forEach { collection ->
                    val kind = repository.collectionKind(collection)
                    val items = try {
                        repository.getCollectionItems(session.userId, collection.Id, kind, SERVICE_ROW_LIMIT)
                    } catch (err: Exception) {
                        emptyList()
                    }
                    if (items.isEmpty()) return@forEach
                    sections.add(HomeSection(title = rowTitle(collection, serviceName, kind), items = items))
                    // Real screens/service.js's own pickHeroItem(): whichever
                    // real item across every matched collection carries a
                    // real backdrop and rates highest, not always row
                    // zero's own first card.
                    items.forEach { item ->
                        if (item.BackdropImageTags.isNullOrEmpty()) return@forEach
                        val rating = item.CommunityRating ?: 0.0
                        if (heroItem == null || rating > heroRating) {
                            heroItem = item
                            heroRating = rating
                        }
                    }
                }

                _uiState.value = ServiceUiState(
                    isLoading = false,
                    serviceName = serviceName,
                    heroItem = heroItem,
                    sections = sections,
                )
            } catch (err: Exception) {
                _uiState.value = ServiceUiState(isLoading = false, error = err.message ?: "Could not load $serviceName", serviceName = serviceName)
            }
        }
    }
}
