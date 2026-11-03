package com.jellio.tv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.logoUrl
import com.jellio.tv.data.prefs.StreamPreferences
import com.jellio.tv.data.session.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AuthState {
    data object Loading : AuthState
    data object LoggedOut : AuthState
    data class LoggedIn(val session: Session) : AuthState
}

sealed interface PlayAction {
    data class Direct(val itemId: String, val mediaSourceId: String?) : PlayAction
    data class ShowPicker(val item: BaseItemDto) : PlayAction
}

@HiltViewModel
class AppViewModel @Inject constructor(
    private val repository: JellioRepository,
    private val streamPreferences: StreamPreferences,
    private val watchNextSyncer: com.jellio.tv.data.watchnext.WatchNextSyncer,
) : ViewModel() {
    val authState: StateFlow<AuthState> = repository.sessionFlow
        .map { session -> if (session != null) AuthState.LoggedIn(session) else AuthState.LoggedOut }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AuthState.Loading)

    private val _libraries = MutableStateFlow<List<BaseItemDto>>(emptyList())
    val libraries: StateFlow<List<BaseItemDto>> = _libraries.asStateFlow()

    init {
        viewModelScope.launch {
            authState.collectLatest { state ->
                if (state is AuthState.LoggedIn) {
                    // Right after an update or wake the network or server may not
                    // be ready yet; keep trying instead of leaving the menu empty.
                    var waitMs = 1_000L
                    while (true) {
                        val entries = try {
                            repository.getLibraryNavEntries(state.session.userId)
                        } catch (err: Exception) {
                            emptyList()
                        }
                        if (entries.isNotEmpty()) {
                            _libraries.value = entries
                            break
                        }
                        kotlinx.coroutines.delay(waitMs)
                        waitMs = (waitMs * 2).coerceAtMost(30_000L)
                    }
                } else {
                    _libraries.value = emptyList()
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            watchNextSyncer.clear()
            repository.logout()
        }
    }

    fun imageUrl(session: Session, item: BaseItemDto, imageType: String, maxWidth: Int): String {
        if (imageType == "Logo") {
            val logoTag = item.ImageTags?.get("Logo") ?: item.ParentLogoImageTag
            val targetId = if (item.ImageTags?.containsKey("Logo") == true) {
                item.Id
            } else if (!item.ParentLogoItemId.isNullOrBlank()) {
                item.ParentLogoItemId!!
            } else if (!item.SeriesId.isNullOrBlank()) {
                item.SeriesId!!
            } else {
                item.Id
            }
            return repository.imageUrl(session.serverAddress, targetId, logoTag, "Logo", maxWidth, session.accessToken)
        }
        val backdropTag = item.BackdropImageTags?.firstOrNull() ?: item.ParentBackdropImageTags?.firstOrNull()
        val primaryTag = item.ImageTags?.get("Primary") ?: item.PrimaryImageTag
        val resolvedType = if (imageType == "Backdrop" && backdropTag == null && primaryTag != null) "Primary" else imageType
        val tag = when (resolvedType) {
            "Backdrop" -> backdropTag
            "Primary" -> primaryTag
            "Thumb" -> item.ImageTags?.get("Thumb") ?: item.ThumbImageTag ?: item.ParentThumbImageTag
            else -> item.ImageTags?.get(resolvedType)
        }
        val targetId = if (resolvedType == "Backdrop" && item.BackdropImageTags.isNullOrEmpty() && !item.ParentBackdropItemId.isNullOrBlank()) {
            item.ParentBackdropItemId!!
        } else item.Id
        return repository.imageUrl(session.serverAddress, targetId, tag, resolvedType, maxWidth, session.accessToken)
    }

    fun rawImageUrl(session: Session, itemId: String, tag: String?, imageType: String, maxWidth: Int): String =
        repository.imageUrl(session.serverAddress, itemId, tag, imageType, maxWidth, session.accessToken)

    fun userImageUrl(session: Session, userId: String, tag: String?, maxWidth: Int): String =
        repository.userImageUrl(session.serverAddress, userId, tag, maxWidth, session.accessToken)

    fun bannerUrl(session: Session, userId: String): String =
        repository.bannerUrl(session.serverAddress, session.accessToken, userId)

    fun serviceLogoUrl(session: Session, name: String): String = logoUrl(session.serverAddress, name)

    suspend fun getMediaSources(session: Session, itemId: String, forceFresh: Boolean = false) =
        repository.getMediaSources(session.userId, itemId, forceFresh = forceFresh)

    suspend fun readyInfo(itemId: String): com.jellio.tv.data.streams.ReadyInfo? = kotlinx.coroutines.coroutineScope {
        val tiers = async { repository.getQualityTiers(itemId) }
        val warm = repository.getWarmSources(itemId) ?: return@coroutineScope null
        com.jellio.tv.data.streams.ReadyInfo(
            warm.keys.map { com.jellio.tv.data.streams.normalizeSourceId(it) }.toSet(),
            tiers.await().mapKeys { com.jellio.tv.data.streams.normalizeSourceId(it.key) },
        )
    }

    suspend fun rememberStreamChoice(itemId: String, mediaSourceId: String) {
        if (streamPreferences.isRememberEnabled()) streamPreferences.remember(itemId, mediaSourceId)
    }

    suspend fun rememberedMediaSourceId(itemId: String): String? = streamPreferences.rememberedMediaSourceId(itemId)

    suspend fun getItemOrNull(session: Session, itemId: String): BaseItemDto? =
        runCatching { repository.getItem(session.userId, itemId) }.getOrNull()

    suspend fun resolvePlayAction(session: Session, item: BaseItemDto, forceChoice: Boolean = false): PlayAction {
        if (!forceChoice && streamPreferences.isRememberEnabled()) {
            val remembered = streamPreferences.rememberedMediaSourceId(item.Id)
            if (remembered != null) {
                val sources = getMediaSources(session, item.Id)
                if (sources.any { it.Id == remembered && !com.jellio.tv.ui.player.BadStreams.isBad(item.Id, it) }) {
                    return PlayAction.Direct(item.Id, remembered)
                }
            }
        }
        return PlayAction.ShowPicker(item)
    }

    suspend fun getUserConfiguration(userId: String) =
        runCatching { repository.getUser(userId)?.Configuration }.getOrNull()

    fun saveLanguagePreferences(session: Session, audio: String?, subtitle: String?) {
        viewModelScope.launch {
            runCatching { repository.updateLanguagePreferences(session.userId, audio, subtitle) }
        }
    }
}
