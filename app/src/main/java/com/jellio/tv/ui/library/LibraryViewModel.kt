package com.jellio.tv.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.home.HomeSection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val coverflowItems: List<BaseItemDto> = emptyList(),
    val sections: List<HomeSection> = emptyList(),
)

// Real port of screens/library.js: a coverflow carousel (real random
// Movie/Series candidates scoped to this library, same real
// getHeroCandidates() call, below its own real MIN_SLIDES floor the
// carousel just does not mount) over a main "Recently added" row plus
// one row per real genre the library actually has enough of
// (discoverGenres, the same sampled-and-counted approach that file's
// own runtime/api.js sibling uses).
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: JellioRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var loadedFor: String? = null

    fun load(session: Session, library: BaseItemDto) {
        val key = library.Id
        if (loadedFor == key) return
        loadedFor = key
        viewModelScope.launch {
            _uiState.value = LibraryUiState(isLoading = true, title = library.Name ?: "Library")
            val itemType = if (library.CollectionType == "movies") "Movie" else "Series"

            val coverflowItems = runCatching {
                repository.getHeroCandidates(session.userId, limit = 8, parentId = library.Id)
            }.getOrDefault(emptyList())

            val mainItems = runCatching {
                repository.getLibraryItems(session.userId, library.Id, limit = 20, includeItemTypes = itemType, sortBy = "DateCreated", sortOrder = "Descending")
            }.getOrDefault(emptyList())

            val sections = mutableListOf(HomeSection("Recently Added", mainItems))

            val genres = runCatching { repository.discoverGenres(session.userId, library.Id, itemType) }.getOrDefault(emptyList())
            genres.forEach { genre ->
                val items = runCatching { repository.getGenreItems(session.userId, library.Id, itemType, genre) }.getOrDefault(emptyList())
                if (items.isNotEmpty()) sections.add(HomeSection(genre, items))
            }

            _uiState.value = LibraryUiState(
                isLoading = false,
                title = library.Name ?: "Library",
                coverflowItems = coverflowItems,
                sections = sections,
            )
        }
    }
}
