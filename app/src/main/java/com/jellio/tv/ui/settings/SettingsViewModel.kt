package com.jellio.tv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.model.AvatarPresetDto
import com.jellio.tv.data.model.matchLanguageOption
import com.jellio.tv.data.prefs.StreamPreferences
import com.jellio.tv.data.session.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val removeFromWatchlistAfterWatching: Boolean = true,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: JellioRepository,
    private val streamPreferences: StreamPreferences,
    private val recommendationPreferences: com.jellio.tv.data.prefs.RecommendationPreferences,
) : ViewModel() {
    private val _communityRowEnabled = MutableStateFlow(true)
    val communityRowEnabled: StateFlow<Boolean> = _communityRowEnabled.asStateFlow()

    private val _communityUsers = MutableStateFlow<List<com.jellio.tv.data.model.CommunityUserDto>>(emptyList())
    val communityUsers: StateFlow<List<com.jellio.tv.data.model.CommunityUserDto>> = _communityUsers.asStateFlow()

    private val _excludedUserIds = MutableStateFlow<Set<String>>(emptySet())
    val excludedUserIds: StateFlow<Set<String>> = _excludedUserIds.asStateFlow()

    private val _allGenres = MutableStateFlow<List<String>>(emptyList())
    val allGenres: StateFlow<List<String>> = _allGenres.asStateFlow()

    private val _selectedGenres = MutableStateFlow<List<String>>(emptyList())
    val selectedGenres: StateFlow<List<String>> = _selectedGenres.asStateFlow()

    private val _customGenres = MutableStateFlow<List<com.jellio.tv.data.model.CustomGenreItem>>(emptyList())
    val customGenres: StateFlow<List<com.jellio.tv.data.model.CustomGenreItem>> = _customGenres.asStateFlow()

    private val _genreSearchQuery = MutableStateFlow("")
    val genreSearchQuery: StateFlow<String> = _genreSearchQuery.asStateFlow()

    private val _genreSearchResult = MutableStateFlow<com.jellio.tv.data.model.GenreSearchResultDto?>(null)
    val genreSearchResult: StateFlow<com.jellio.tv.data.model.GenreSearchResultDto?> = _genreSearchResult.asStateFlow()

    private val _genreSearching = MutableStateFlow(false)
    val genreSearching: StateFlow<Boolean> = _genreSearching.asStateFlow()

    private val _rememberStream = MutableStateFlow(true)
    val rememberStream: StateFlow<Boolean> = _rememberStream.asStateFlow()

    private val _audioLanguage = MutableStateFlow<String?>(null)
    val audioLanguage: StateFlow<String?> = _audioLanguage.asStateFlow()

    private val _subtitleLanguage = MutableStateFlow<String?>(null)
    val subtitleLanguage: StateFlow<String?> = _subtitleLanguage.asStateFlow()

    private val _languageStatus = MutableStateFlow<String?>(null)
    val languageStatus: StateFlow<String?> = _languageStatus.asStateFlow()

    private val _isUpdatingPassword = MutableStateFlow(false)
    val isUpdatingPassword: StateFlow<Boolean> = _isUpdatingPassword.asStateFlow()

    private val _passwordStatus = MutableStateFlow<String?>(null)
    val passwordStatus: StateFlow<String?> = _passwordStatus.asStateFlow()

    private val _passwordUpdateTick = MutableStateFlow(0)
    val passwordUpdateTick: StateFlow<Int> = _passwordUpdateTick.asStateFlow()

    private val _sleepTimerStatusText = MutableStateFlow("No active playback session.")
    val sleepTimerStatusText: StateFlow<String> = _sleepTimerStatusText.asStateFlow()

    private val _sleepTimerActive = MutableStateFlow(false)
    val sleepTimerActive: StateFlow<Boolean> = _sleepTimerActive.asStateFlow()

    private val _isCancellingSleepTimer = MutableStateFlow(false)
    val isCancellingSleepTimer: StateFlow<Boolean> = _isCancellingSleepTimer.asStateFlow()

    private val _showAvatarPicker = MutableStateFlow(false)
    val showAvatarPicker: StateFlow<Boolean> = _showAvatarPicker.asStateFlow()

    private val _isLoadingAvatarPresets = MutableStateFlow(false)
    val isLoadingAvatarPresets: StateFlow<Boolean> = _isLoadingAvatarPresets.asStateFlow()

    private val _avatarPresets = MutableStateFlow<List<AvatarPresetDto>>(emptyList())
    val avatarPresets: StateFlow<List<AvatarPresetDto>> = _avatarPresets.asStateFlow()

    private val _avatarPickerStatus = MutableStateFlow<String?>(null)
    val avatarPickerStatus: StateFlow<String?> = _avatarPickerStatus.asStateFlow()

    private val _avatarBusyKey = MutableStateFlow<String?>(null)
    val avatarBusyKey: StateFlow<String?> = _avatarBusyKey.asStateFlow()

    private val _isPrivate = MutableStateFlow(false)
    val isPrivate: StateFlow<Boolean> = _isPrivate.asStateFlow()

    private val _removeFromWatchlistAfterWatching = MutableStateFlow(true)
    val removeFromWatchlistAfterWatching: StateFlow<Boolean> = _removeFromWatchlistAfterWatching.asStateFlow()

    private var loadedUserId: String? = null

    init {
        viewModelScope.launch { _rememberStream.value = streamPreferences.isRememberEnabled() }
        viewModelScope.launch {
            val result = repository.getSleepTimerStatus() ?: return@launch
            _sleepTimerActive.value = result.Active
            _sleepTimerStatusText.value = if (result.Active) "A sleep timer is running." else "No sleep timer is running."
        }
    }

    fun setRememberStream(enabled: Boolean) {
        _rememberStream.value = enabled
        viewModelScope.launch { streamPreferences.setRememberEnabled(enabled) }
    }

    fun load(session: Session) {
        if (loadedUserId == session.userId) return
        loadedUserId = session.userId
        viewModelScope.launch {
            val user = runCatching { repository.getUser(session.userId) }.getOrNull() ?: return@launch
            user.Configuration?.let { configuration ->
                _audioLanguage.value = matchLanguageOption(configuration.AudioLanguagePreference)?.code
                _subtitleLanguage.value = if (configuration.SubtitleMode.equals("None", ignoreCase = true) || configuration.SubtitleLanguagePreference == "off") "off" else matchLanguageOption(configuration.SubtitleLanguagePreference)?.code
            }
        }
        viewModelScope.launch {
            _removeFromWatchlistAfterWatching.value = repository.isRemoveFromWatchlistAfterWatchingEnabled()
            val settings = runCatching { repository.getProfileSettings() }.getOrNull() ?: return@launch
            _isPrivate.value = settings.IsPrivate
            _removeFromWatchlistAfterWatching.value = settings.RemoveFromWatchlistAfterWatching
        }
    }

    fun setRemoveFromWatchlistAfterWatching(enabled: Boolean) {
        val previous = _removeFromWatchlistAfterWatching.value
        _removeFromWatchlistAfterWatching.value = enabled
        viewModelScope.launch {
            runCatching { repository.setRemoveFromWatchlistAfterWatching(enabled) }
                .onFailure { _removeFromWatchlistAfterWatching.value = previous }
        }
    }

    fun setPrivate(enabled: Boolean) {
        val previous = _isPrivate.value
        _isPrivate.value = enabled
        viewModelScope.launch {
            runCatching { repository.setProfilePrivacy(enabled) }.onFailure { _isPrivate.value = previous }
        }
    }

    fun setAudioLanguage(session: Session, code: String?) {
        _audioLanguage.value = code
        saveLanguagePreferences(session, code, _subtitleLanguage.value)
    }

    fun setSubtitleLanguage(session: Session, code: String?) {
        _subtitleLanguage.value = code
        saveLanguagePreferences(session, _audioLanguage.value, code)
    }

    private fun saveLanguagePreferences(session: Session, audio: String?, subtitle: String?) {
        viewModelScope.launch {
            _languageStatus.value = "Saving…"
            val saved = runCatching { repository.updateLanguagePreferences(session.userId, audio, subtitle) }
                .onFailure { android.util.Log.w("JellioSettings", "language save reported an error", it) }
                .isSuccess
            if (saved) {
                _languageStatus.value = "Saved, takes effect the next time you start playback."
                return@launch
            }
            val stored = runCatching { repository.getUserFresh(session.userId).Configuration }.getOrNull()
            if (stored != null) {
                _audioLanguage.value = matchLanguageOption(stored.AudioLanguagePreference)?.code
                _subtitleLanguage.value = matchLanguageOption(stored.SubtitleLanguagePreference)?.code
            }
            val matches = stored != null && _audioLanguage.value == audio && _subtitleLanguage.value == subtitle
            _languageStatus.value = if (matches) com.jellio.tv.ui.i18n.Strings.get("settings_saved_next_playback") else com.jellio.tv.ui.i18n.Strings.get("settings_save_lang_error")
        }
    }

    fun updatePassword(session: Session, currentPassword: String, newPassword: String, confirmPassword: String) {
        if (newPassword.isEmpty() || newPassword != confirmPassword) {
            _passwordStatus.value = com.jellio.tv.ui.i18n.Strings.get("settings_passwords_mismatch")
            return
        }
        viewModelScope.launch {
            _isUpdatingPassword.value = true
            _passwordStatus.value = "Updating…"
            try {
                repository.updatePassword(session.userId, currentPassword, newPassword)
                _passwordStatus.value = "Password updated."
                _passwordUpdateTick.value += 1
            } catch (err: Exception) {
                _passwordStatus.value = "Could not update password. Check your current password."
            } finally {
                _isUpdatingPassword.value = false
            }
        }
    }

    fun cancelSleepTimer() {
        viewModelScope.launch {
            _isCancellingSleepTimer.value = true
            try {
                repository.cancelSleepTimer()
                _sleepTimerStatusText.value = "Sleep timer cancelled."
                _sleepTimerActive.value = false
            } finally {
                _isCancellingSleepTimer.value = false
            }
        }
    }

    fun openAvatarPicker() {
        _showAvatarPicker.value = true
        _avatarPickerStatus.value = null
        _avatarBusyKey.value = null
        viewModelScope.launch {
            _isLoadingAvatarPresets.value = true
            try {
                val presets = repository.getAvatarPresets()
                _avatarPresets.value = presets
                if (presets.isEmpty()) {
                    _avatarPickerStatus.value = com.jellio.tv.ui.i18n.Strings.get("settings_no_preset_avatars")
                }
            } catch (err: Exception) {
                _avatarPickerStatus.value = com.jellio.tv.ui.i18n.Strings.get("settings_load_avatars_error")
            } finally {
                _isLoadingAvatarPresets.value = false
            }
        }
    }

    fun closeAvatarPicker() {
        _showAvatarPicker.value = false
    }

    fun avatarPresetUrl(session: Session, id: String): String =
        repository.avatarPresetUrl(session.serverAddress, session.accessToken, id)

    fun selectAvatarPreset(session: Session, presetId: String) {
        if (_avatarBusyKey.value != null) return
        viewModelScope.launch {
            _avatarBusyKey.value = presetId
            _avatarPickerStatus.value = com.jellio.tv.ui.i18n.Strings.get("settings_setting_avatar")
            try {
                repository.setUserAvatarFromPreset(session.userId, presetId)
                _avatarPickerStatus.value = com.jellio.tv.ui.i18n.Strings.get("settings_avatar_updated")
                _showAvatarPicker.value = false
            } catch (err: Exception) {
                _avatarPickerStatus.value = com.jellio.tv.ui.i18n.Strings.get("settings_set_avatar_error")
                _avatarBusyKey.value = null
            }
        }
    }

    fun uploadAvatar(session: Session, bytes: ByteArray, contentType: String) {
        if (_avatarBusyKey.value != null) return
        viewModelScope.launch {
            _avatarBusyKey.value = "upload"
            _avatarPickerStatus.value = com.jellio.tv.ui.i18n.Strings.get("settings_setting_avatar")
            try {
                repository.setUserAvatarFromBytes(session.userId, bytes, contentType)
                _avatarPickerStatus.value = com.jellio.tv.ui.i18n.Strings.get("settings_avatar_updated")
                _showAvatarPicker.value = false
            } catch (err: Exception) {
                _avatarPickerStatus.value = com.jellio.tv.ui.i18n.Strings.get("settings_set_avatar_error")
                _avatarBusyKey.value = null
            }
        }
    }

    fun loadRecommendations(userId: String) {
        viewModelScope.launch {
            _communityRowEnabled.value = recommendationPreferences.isCommunityRowEnabled(userId)
            _excludedUserIds.value = recommendationPreferences.getCommunityExcludedUsers(userId)
            _selectedGenres.value = recommendationPreferences.getSelectedGenres(userId)
            _customGenres.value = recommendationPreferences.getCustomGenres(userId)

            runCatching {
                val profile = repository.getProfileSettings()
                var pushNeeded = false
                var toPushSelected = profile.selectedGenres
                var toPushCustom = profile.customGenres

                if (profile.selectedGenres != null) {
                    recommendationPreferences.setSelectedGenres(userId, profile.selectedGenres)
                    _selectedGenres.value = profile.selectedGenres
                } else if (_selectedGenres.value.isNotEmpty()) {
                    pushNeeded = true
                    toPushSelected = _selectedGenres.value
                }

                if (profile.customGenres != null) {
                    recommendationPreferences.setCustomGenres(userId, profile.customGenres)
                    _customGenres.value = profile.customGenres
                } else if (_customGenres.value.isNotEmpty()) {
                    pushNeeded = true
                    toPushCustom = _customGenres.value
                }

                if (pushNeeded) {
                    runCatching {
                        repository.saveProfileRecommendations(
                            selectedGenres = toPushSelected,
                            customGenres = toPushCustom,
                        )
                    }
                }
            }

            runCatching {
                _communityUsers.value = repository.getCommunityUsers()
            }
            runCatching {
                _allGenres.value = repository.getAllGenres(userId, null, "Movie,Series").sortedBy { com.jellio.tv.ui.i18n.genreDisplayName(it) }
            }
        }
    }

    private fun syncRecommendationsToServer() {
        viewModelScope.launch {
            runCatching {
                repository.saveProfileRecommendations(
                    selectedGenres = _selectedGenres.value,
                    customGenres = _customGenres.value,
                )
            }
        }
    }

    fun setCommunityRowEnabled(userId: String, enabled: Boolean) {
        _communityRowEnabled.value = enabled
        viewModelScope.launch {
            recommendationPreferences.setCommunityRowEnabled(userId, enabled)
        }
    }

    fun toggleCommunityExcludedUser(userId: String, targetUserId: String) {
        viewModelScope.launch {
            recommendationPreferences.toggleCommunityExcludedUser(userId, targetUserId)
            _excludedUserIds.value = recommendationPreferences.getCommunityExcludedUsers(userId)
        }
    }

    fun toggleSelectedGenre(userId: String, genre: String) {
        viewModelScope.launch {
            _selectedGenres.value = recommendationPreferences.toggleSelectedGenre(userId, genre)
            syncRecommendationsToServer()
        }
    }

    fun resetSelectedGenres(userId: String) {
        _selectedGenres.value = emptyList()
        viewModelScope.launch {
            recommendationPreferences.setSelectedGenres(userId, emptyList())
            syncRecommendationsToServer()
        }
    }

    fun searchGenre(query: String) {
        _genreSearchQuery.value = query
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _genreSearchResult.value = null
            _genreSearching.value = false
            return
        }
        _genreSearching.value = true
        viewModelScope.launch {
            val result = repository.searchGenreOrCategory(trimmed)
            _genreSearchResult.value = result
            _genreSearching.value = false
        }
    }

    fun addCustomGenre(userId: String, item: com.jellio.tv.data.model.CustomGenreItem) {
        viewModelScope.launch {
            _customGenres.value = recommendationPreferences.addCustomGenre(userId, item)
            _genreSearchResult.value = null
            _genreSearchQuery.value = ""
            syncRecommendationsToServer()
        }
    }

    fun removeCustomGenre(userId: String, name: String) {
        viewModelScope.launch {
            _customGenres.value = recommendationPreferences.removeCustomGenre(userId, name)
            syncRecommendationsToServer()
        }
    }

    fun toggleCustomGenre(userId: String, name: String) {
        viewModelScope.launch {
            _customGenres.value = recommendationPreferences.toggleCustomGenre(userId, name)
            syncRecommendationsToServer()
        }
    }
}
