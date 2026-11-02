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
            val settings = runCatching { repository.getProfileSettings() }.getOrNull() ?: return@launch
            _isPrivate.value = settings.IsPrivate
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
            _languageStatus.value = if (matches) "Saved, takes effect the next time you start playback." else "Could not save language preferences."
        }
    }

    fun updatePassword(session: Session, currentPassword: String, newPassword: String, confirmPassword: String) {
        if (newPassword.isEmpty() || newPassword != confirmPassword) {
            _passwordStatus.value = "New passwords do not match."
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
                    _avatarPickerStatus.value = "No preset avatars are available on this server, upload your own instead."
                }
            } catch (err: Exception) {
                _avatarPickerStatus.value = "Could not load avatars."
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
            _avatarPickerStatus.value = "Setting avatar…"
            try {
                repository.setUserAvatarFromPreset(session.userId, presetId)
                _avatarPickerStatus.value = "Avatar updated."
                _showAvatarPicker.value = false
            } catch (err: Exception) {
                _avatarPickerStatus.value = "Could not set that avatar."
                _avatarBusyKey.value = null
            }
        }
    }

    fun uploadAvatar(session: Session, bytes: ByteArray, contentType: String) {
        if (_avatarBusyKey.value != null) return
        viewModelScope.launch {
            _avatarBusyKey.value = "upload"
            _avatarPickerStatus.value = "Setting avatar…"
            try {
                repository.setUserAvatarFromBytes(session.userId, bytes, contentType)
                _avatarPickerStatus.value = "Avatar updated."
                _showAvatarPicker.value = false
            } catch (err: Exception) {
                _avatarPickerStatus.value = "Could not upload that picture."
                _avatarBusyKey.value = null
            }
        }
    }

    fun loadRecommendations(userId: String) {
        viewModelScope.launch {
            _communityRowEnabled.value = recommendationPreferences.isCommunityRowEnabled(userId)
            _excludedUserIds.value = recommendationPreferences.getCommunityExcludedUsers(userId)
            _selectedGenres.value = recommendationPreferences.getSelectedGenres(userId)

            runCatching {
                _communityUsers.value = repository.getCommunityUsers()
            }
            runCatching {
                _allGenres.value = repository.getAllGenres(userId, null, "Movie,Series").sortedBy { com.jellio.tv.ui.i18n.genreDisplayName(it) }
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
        }
    }

    fun resetSelectedGenres(userId: String) {
        _selectedGenres.value = emptyList()
        viewModelScope.launch {
            recommendationPreferences.setSelectedGenres(userId, emptyList())
        }
    }
}
