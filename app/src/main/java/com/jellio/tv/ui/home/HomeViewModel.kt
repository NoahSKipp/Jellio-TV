package com.jellio.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.recommend.RecommendationDataSource
import com.jellio.tv.data.recommend.buildRecommendationRows
import com.jellio.tv.data.recommend.titleKey
import com.jellio.tv.data.session.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeSection(val title: String, val items: List<BaseItemDto>)

data class HomeUiState(
    val isLoading: Boolean = true,
    val heroItem: BaseItemDto? = null,
    val sections: List<HomeSection> = emptyList(),
    val error: String? = null,
)

// Real per-library rows plus Continue Watching/Up Next/recommendation
// rows, mirroring screens/home.js's own buildHomeSections() at a
// reduced but real scale: Continue Watching then Up Next is the real
// order real feedback settled on there, RecommendationEngine's own
// real "Top Picks for You"/"Because you watched X"/"Because you're
// watching X"/"More with X" rows next (real feedback: the genre
// aggregate row leads, ahead of every per-title row), per-library rows
// last, this app's own stand in for screens/home.js's own real Coming
// Soon/Studio Hub/catalog/genre rows until the matching real
// Gelato-backed catalog logic earns its own real row here.
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: JellioRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var loadedForUserId: String? = null

    fun load(session: Session) {
        if (loadedForUserId == session.userId) return
        loadedForUserId = session.userId
        viewModelScope.launch {
            _uiState.value = HomeUiState(isLoading = true)
            try {
                val libraries = repository.getLibraries(session.userId)
                    .filter { it.CollectionType == "movies" || it.CollectionType == "tvshows" }

                val continueWatching = repository.getContinueWatching(session.userId)
                val upNext = try {
                    repository.getNextUp(session.userId)
                } catch (err: Exception) {
                    emptyList()
                }

                // Real web hero (runtime/api.js's own getHeroCandidates())
                // never draws from Continue Watching at all, only a
                // random real Movie/Series: fetched independently rather
                // than reusing whatever led the rows above.
                val heroCandidates = try {
                    repository.getHeroCandidates(session.userId)
                } catch (err: Exception) {
                    emptyList()
                }

                val recommendationSource = RecommendationDataSource(
                    getRecentlyCompleted = { limit -> repository.getRecentlyCompleted(session.userId, limit) },
                    getNextUp = { limit -> repository.getNextUp(session.userId, limit) },
                    getRecommendationCandidates = { seed, limit -> repository.getRecommendationCandidates(session.userId, seed, limit) },
                    getGenreItems = { genre, limit -> repository.getGenreItems(session.userId, null, "Movie,Series", genre, limit) },
                    getPersonItems = { personId, limit -> repository.getPersonItems(session.userId, personId, limit) },
                )
                // Shared across recommendation and per-library rows, same
                // real reasoning screens/home.js's own shared `seen` object
                // documents: a title one row already picked should not
                // also turn up further down the same page.
                val exclude = mutableSetOf<String>()
                val recommendationRows = try {
                    buildRecommendationRows(recommendationSource, exclude)
                } catch (err: Exception) {
                    emptyList()
                }

                val libraryRows = libraries.map { library ->
                    val items = repository.getLibraryItems(session.userId, library.Id)
                        .filter { !exclude.contains(it.Id) && !exclude.contains(titleKey(it)) }
                    HomeSection(title = library.Name ?: "Library", items = items)
                }

                val sections = buildList {
                    if (continueWatching.isNotEmpty()) {
                        add(HomeSection("Continue Watching", continueWatching))
                    }
                    if (upNext.isNotEmpty()) {
                        add(HomeSection("Up Next", upNext))
                    }
                    addAll(recommendationRows)
                    addAll(libraryRows.filter { it.items.isNotEmpty() })
                }

                val hero = heroCandidates.firstOrNull()

                _uiState.value = HomeUiState(isLoading = false, heroItem = hero, sections = sections)
            } catch (err: Exception) {
                _uiState.value = HomeUiState(isLoading = false, error = err.message ?: "Could not load Home")
            }
        }
    }
}
