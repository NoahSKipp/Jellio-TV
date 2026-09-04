package com.jellio.tv.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.model.AchievementsDto
import com.jellio.tv.data.model.ProfileDto
import com.jellio.tv.data.model.UserDto
import com.jellio.tv.data.session.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val user: UserDto? = null,
    val profile: ProfileDto? = null,
    val achievements: AchievementsDto? = null,
    val isOwner: Boolean = false,
    val isEditingBio: Boolean = false,
    val bioDraft: String = "",
    val isSavingBio: Boolean = false,
    val bannerBustToken: Long = 0L,
)

// Real port of screens/profile.js's own renderProfile(): the same real
// Promise.all([getUserById, getProfileForUser, getAchievementsForUser])
// fan out, LibraryViewModel's own async {}/awaitAll() pattern for it
// rather than a bare Promise.all equivalent.
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: JellioRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private var loadedFor: String? = null

    fun load(session: Session, targetUserId: String?) {
        val resolvedUserId = targetUserId ?: session.userId
        if (loadedFor == resolvedUserId) return
        loadedFor = resolvedUserId
        fetch(session, resolvedUserId)
    }

    fun retry(session: Session, targetUserId: String?) {
        loadedFor = null
        load(session, targetUserId)
    }

    private fun fetch(session: Session, userId: String) = viewModelScope.launch {
        _uiState.value = ProfileUiState(isLoading = true)
        runCatching {
            coroutineScope {
                val userDeferred = async { repository.getUser(userId) }
                val profileDeferred = async { repository.getProfile(userId) }
                val achievementsDeferred = async { repository.getAchievements(userId) }
                Triple(userDeferred.await(), profileDeferred.await(), achievementsDeferred.await())
            }
        }.onSuccess { (user, profile, achievements) ->
            _uiState.value = ProfileUiState(
                isLoading = false,
                user = user,
                profile = profile,
                achievements = achievements,
                isOwner = userId == session.userId,
            )
        }.onFailure { err ->
            _uiState.value = ProfileUiState(isLoading = false, error = err.message ?: "Could not load this profile.")
        }
    }

    fun startEditingBio() {
        _uiState.value = _uiState.value.copy(isEditingBio = true, bioDraft = _uiState.value.profile?.Bio ?: "")
    }

    fun cancelEditingBio() {
        _uiState.value = _uiState.value.copy(isEditingBio = false)
    }

    fun updateBioDraft(text: String) {
        // Real ProfileController.cs's own real 240 char server side
        // cap: clamped here too so the field itself never visibly
        // overflows before a save round trip would have caught it.
        _uiState.value = _uiState.value.copy(bioDraft = text.take(240))
    }

    fun saveBio() {
        val state = _uiState.value
        val trimmed = state.bioDraft.trim().ifEmpty { null }
        viewModelScope.launch {
            _uiState.value = state.copy(isSavingBio = true)
            runCatching { repository.setProfileBio(trimmed) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isSavingBio = false,
                        isEditingBio = false,
                        profile = _uiState.value.profile?.copy(Bio = trimmed),
                    )
                }
                .onFailure { _uiState.value = _uiState.value.copy(isSavingBio = false) }
        }
    }

    fun uploadBanner(bytes: ByteArray, contentType: String) {
        viewModelScope.launch {
            runCatching { repository.setProfileBannerFromBytes(bytes, contentType) }
                .onSuccess { _uiState.value = _uiState.value.copy(bannerBustToken = System.currentTimeMillis()) }
        }
    }
}
