package com.jellio.tv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.catching
import com.jellio.tv.data.model.AvatarPresetDto
import com.jellio.tv.data.model.UserConfigurationDto
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

    private val _customGenres = MutableStateFlow<List<com.jellio.tv.data.model.CustomGenreItem>>(emptyList())
    val customGenres: StateFlow<List<com.jellio.tv.data.model.CustomGenreItem>> = _customGenres.asStateFlow()

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

    private val _isUpdatingPassword = MutableStateFlow(false)
    val isUpdatingPassword: StateFlow<Boolean> = _isUpdatingPassword.asStateFlow()

    private val _passwordStatus = MutableStateFlow<String?>(null)
    val passwordStatus: StateFlow<String?> = _passwordStatus.asStateFlow()

    private val _passwordUpdateTick = MutableStateFlow(0)
    val passwordUpdateTick: StateFlow<Int> = _passwordUpdateTick.asStateFlow()

    private val _sleepTimerActive = MutableStateFlow(false)
    val sleepTimerActive: StateFlow<Boolean> = _sleepTimerActive.asStateFlow()

    private val _isCancellingSleepTimer = MutableStateFlow(false)
    val isCancellingSleepTimer: StateFlow<Boolean> = _isCancellingSleepTimer.asStateFlow()

    private val _showAvatarPicker = MutableStateFlow(false)
    val showAvatarPicker: StateFlow<Boolean> = _showAvatarPicker.asStateFlow()

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

    private val _aniSyncSettings = MutableStateFlow<com.jellio.tv.data.model.AniSyncSettingsDto?>(null)
    val aniSyncSettings: StateFlow<com.jellio.tv.data.model.AniSyncSettingsDto?> = _aniSyncSettings.asStateFlow()

    private val _aniSyncLoading = MutableStateFlow(false)
    val aniSyncLoading: StateFlow<Boolean> = _aniSyncLoading.asStateFlow()

    private val _aniSyncActionBusy = MutableStateFlow<String?>(null)
    val aniSyncActionBusy: StateFlow<String?> = _aniSyncActionBusy.asStateFlow()

    private val _aniSyncMessage = MutableStateFlow<String?>(null)
    val aniSyncMessage: StateFlow<String?> = _aniSyncMessage.asStateFlow()

    private val _simklPinData = MutableStateFlow<com.jellio.tv.data.model.SimklPinStartDto?>(null)
    val simklPinData: StateFlow<com.jellio.tv.data.model.SimklPinStartDto?> = _simklPinData.asStateFlow()

    private val _simklPinPolling = MutableStateFlow(false)
    val simklPinPolling: StateFlow<Boolean> = _simklPinPolling.asStateFlow()

    private var loadedUserId: String? = null

    init {
        viewModelScope.launch { _rememberStream.value = streamPreferences.isRememberEnabled() }
        viewModelScope.launch {
            val result = repository.getSleepTimerStatus() ?: return@launch
            _sleepTimerActive.value = result.Active
        }
    }

    fun setRememberStream(enabled: Boolean) {
        _rememberStream.value = enabled
        viewModelScope.launch { streamPreferences.setRememberEnabled(enabled) }
    }

    fun load(session: Session) {
        if (loadedUserId == session.userId) return
        loadedUserId = session.userId
        _aniSyncSettings.value = null
        _aniSyncMessage.value = null
        dismissSimklPin()
        viewModelScope.launch {
            val user = catching { repository.getUser(session.userId) }.getOrNull() ?: return@launch
            user.Configuration?.let { configuration ->
                _audioLanguage.value = matchLanguageOption(configuration.AudioLanguagePreference)?.code
                _subtitleLanguage.value = subtitleCode(configuration)
            }
        }
        viewModelScope.launch {
            _removeFromWatchlistAfterWatching.value = repository.isRemoveFromWatchlistAfterWatchingEnabled()
            val settings = catching { repository.getProfileSettings() }.getOrNull() ?: return@launch
            _isPrivate.value = settings.IsPrivate
            _removeFromWatchlistAfterWatching.value = settings.RemoveFromWatchlistAfterWatching
        }
        loadAniSync()
    }

    fun setRemoveFromWatchlistAfterWatching(enabled: Boolean) {
        val previous = _removeFromWatchlistAfterWatching.value
        _removeFromWatchlistAfterWatching.value = enabled
        viewModelScope.launch {
            catching { repository.setRemoveFromWatchlistAfterWatching(enabled) }
                .onFailure { _removeFromWatchlistAfterWatching.value = previous }
        }
    }

    fun setPrivate(enabled: Boolean) {
        val previous = _isPrivate.value
        _isPrivate.value = enabled
        viewModelScope.launch {
            catching { repository.setProfilePrivacy(enabled) }.onFailure { _isPrivate.value = previous }
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

    private fun subtitleCode(configuration: UserConfigurationDto): String? =
        if (configuration.SubtitleMode.equals("None", ignoreCase = true) || configuration.SubtitleLanguagePreference == "off") "off"
        else matchLanguageOption(configuration.SubtitleLanguagePreference)?.code

    private fun saveLanguagePreferences(session: Session, audio: String?, subtitle: String?) {
        viewModelScope.launch {
            val saved = catching { repository.updateLanguagePreferences(session.userId, audio, subtitle) }
                .onFailure { android.util.Log.w("JellioSettings", "language save reported an error", it) }
                .isSuccess
            if (saved) return@launch
            val stored = catching { repository.getUserFresh(session.userId).Configuration }.getOrNull()
            if (stored != null) {
                _audioLanguage.value = matchLanguageOption(stored.AudioLanguagePreference)?.code
                _subtitleLanguage.value = subtitleCode(stored)
            }
        }
    }

    fun updatePassword(session: Session, currentPassword: String, newPassword: String, confirmPassword: String) {
        if (newPassword.isEmpty() || newPassword != confirmPassword) {
            _passwordStatus.value = com.jellio.tv.data.i18n.Strings.get("settings_passwords_mismatch")
            return
        }
        viewModelScope.launch {
            _isUpdatingPassword.value = true
            _passwordStatus.value = com.jellio.tv.data.i18n.Strings.get("settings_updating_password")
            try {
                repository.updatePassword(session.userId, currentPassword, newPassword)
                _passwordStatus.value = com.jellio.tv.data.i18n.Strings.get("settings_password_updated")
                _passwordUpdateTick.value += 1
            } catch (err: Exception) {
                _passwordStatus.value = com.jellio.tv.data.i18n.Strings.get("settings_password_update_failed")
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
            try {
                val presets = repository.getAvatarPresets()
                _avatarPresets.value = presets
                if (presets.isEmpty()) {
                    _avatarPickerStatus.value = com.jellio.tv.data.i18n.Strings.get("settings_no_preset_avatars")
                }
            } catch (err: Exception) {
                _avatarPickerStatus.value = com.jellio.tv.data.i18n.Strings.get("settings_load_avatars_error")
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
            _avatarPickerStatus.value = com.jellio.tv.data.i18n.Strings.get("settings_setting_avatar")
            try {
                repository.setUserAvatarFromPreset(session.userId, presetId)
                _avatarPickerStatus.value = com.jellio.tv.data.i18n.Strings.get("settings_avatar_updated")
                _showAvatarPicker.value = false
            } catch (err: Exception) {
                _avatarPickerStatus.value = com.jellio.tv.data.i18n.Strings.get("settings_set_avatar_error")
                _avatarBusyKey.value = null
            }
        }
    }

    fun uploadAvatar(session: Session, bytes: ByteArray, contentType: String) {
        if (_avatarBusyKey.value != null) return
        viewModelScope.launch {
            _avatarBusyKey.value = "upload"
            _avatarPickerStatus.value = com.jellio.tv.data.i18n.Strings.get("settings_setting_avatar")
            try {
                repository.setUserAvatarFromBytes(session.userId, bytes, contentType)
                _avatarPickerStatus.value = com.jellio.tv.data.i18n.Strings.get("settings_avatar_updated")
                _showAvatarPicker.value = false
            } catch (err: Exception) {
                _avatarPickerStatus.value = com.jellio.tv.data.i18n.Strings.get("settings_set_avatar_error")
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

            catching {
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
                    catching {
                        repository.saveProfileRecommendations(
                            selectedGenres = toPushSelected,
                            customGenres = toPushCustom,
                        )
                    }
                }
            }

            catching {
                _communityUsers.value = repository.getCommunityUsers()
            }
            catching {
                _allGenres.value = repository.getAllGenres(userId, null, "Movie,Series").sortedBy { com.jellio.tv.data.i18n.genreDisplayName(it) }
            }
        }
    }

    private fun syncRecommendationsToServer() {
        viewModelScope.launch {
            catching {
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

    fun loadAniSync() {
        viewModelScope.launch {
            _aniSyncLoading.value = true
            try {
                _aniSyncSettings.value = repository.getAniSyncSettings()
            } catch (err: Exception) {
                android.util.Log.w("JellioAniSync", "Failed to load AniSync settings", err)
            } finally {
                _aniSyncLoading.value = false
            }
        }
    }

    fun selectAniSyncService(serviceId: String) {
        val current = _aniSyncSettings.value ?: return
        if (current.SelectedService.equals(serviceId, ignoreCase = true)) return
        _aniSyncSettings.value = current.copy(SelectedService = serviceId)
        viewModelScope.launch {
            try {
                val updated = repository.updateAniSyncPreferences(selectedService = serviceId)
                _aniSyncSettings.value = updated
            } catch (err: Exception) {
                android.util.Log.w("JellioAniSync", "Failed to update selected AniSync service", err)
                _aniSyncSettings.value = current
            }
        }
    }

    fun setAniSyncEnabled(enabled: Boolean) {
        val current = _aniSyncSettings.value ?: return
        _aniSyncSettings.value = current.copy(Enabled = enabled)
        viewModelScope.launch {
            try {
                val updated = repository.updateAniSyncPreferences(enabled = enabled)
                _aniSyncSettings.value = updated
            } catch (err: Exception) {
                android.util.Log.w("JellioAniSync", "Failed to toggle AniSync enabled", err)
                _aniSyncSettings.value = current
            }
        }
    }

    fun setAniSyncOnlyAnime(onlyAnime: Boolean) {
        val current = _aniSyncSettings.value ?: return
        _aniSyncSettings.value = current.copy(OnlyAnimeItems = onlyAnime)
        viewModelScope.launch {
            try {
                val updated = repository.updateAniSyncPreferences(onlyAnimeItems = onlyAnime)
                _aniSyncSettings.value = updated
            } catch (err: Exception) {
                android.util.Log.w("JellioAniSync", "Failed to toggle AniSync onlyAnime", err)
                _aniSyncSettings.value = current
            }
        }
    }

    fun setAniSyncAllConnected(allConnected: Boolean) {
        val current = _aniSyncSettings.value ?: return
        _aniSyncSettings.value = current.copy(SyncAllConnected = allConnected)
        viewModelScope.launch {
            try {
                val updated = repository.updateAniSyncPreferences(syncAllConnected = allConnected)
                _aniSyncSettings.value = updated
            } catch (err: Exception) {
                android.util.Log.w("JellioAniSync", "Failed to toggle AniSync syncAllConnected", err)
                _aniSyncSettings.value = current
            }
        }
    }

    fun connectAniSync(
        service: String,
        username: String?,
        accessToken: String?,
        refreshToken: String?,
        clientId: String?,
        clientSecret: String?,
    ) {
        viewModelScope.launch {
            _aniSyncActionBusy.value = "connect"
            _aniSyncMessage.value = null
            try {
                val result = repository.connectAniSync(
                    service = service,
                    username = username?.takeIf { it.isNotBlank() },
                    accessToken = accessToken?.takeIf { it.isNotBlank() },
                    refreshToken = refreshToken?.takeIf { it.isNotBlank() },
                    clientId = clientId?.takeIf { it.isNotBlank() },
                    clientSecret = clientSecret?.takeIf { it.isNotBlank() },
                )
                if (result.Settings != null) {
                    _aniSyncSettings.value = result.Settings
                } else {
                    loadAniSync()
                }
                _aniSyncMessage.value = com.jellio.tv.data.i18n.Strings.get("anisync_connected_success")
            } catch (err: Exception) {
                android.util.Log.w("JellioAniSync", "Failed to connect AniSync service", err)
                _aniSyncMessage.value = extractHttpError(err) ?: com.jellio.tv.data.i18n.Strings.get("anisync_connected_failure")
            } finally {
                _aniSyncActionBusy.value = null
            }
        }
    }

    fun disconnectAniSync(service: String) {
        viewModelScope.launch {
            _aniSyncActionBusy.value = "disconnect"
            _aniSyncMessage.value = null
            try {
                val updated = repository.disconnectAniSync(service)
                _aniSyncSettings.value = updated
                _aniSyncMessage.value = com.jellio.tv.data.i18n.Strings.get("anisync_disconnected_success")
            } catch (err: Exception) {
                android.util.Log.w("JellioAniSync", "Failed to disconnect AniSync service", err)
                _aniSyncMessage.value = extractHttpError(err) ?: com.jellio.tv.data.i18n.Strings.get("anisync_disconnected_failure")
            } finally {
                _aniSyncActionBusy.value = null
            }
        }
    }

    fun testAniSync(service: String) {
        viewModelScope.launch {
            _aniSyncActionBusy.value = "test"
            _aniSyncMessage.value = null
            try {
                val result = repository.testAniSync(service)
                if (result.Settings != null) {
                    _aniSyncSettings.value = result.Settings
                }
                _aniSyncMessage.value = if (result.Ok) {
                    result.Message.ifBlank { com.jellio.tv.data.i18n.Strings.get("anisync_test_success") }
                } else {
                    result.Message.ifBlank { com.jellio.tv.data.i18n.Strings.get("anisync_test_failure") }
                }
            } catch (err: Exception) {
                android.util.Log.w("JellioAniSync", "Failed to test AniSync service", err)
                _aniSyncMessage.value = extractHttpError(err) ?: com.jellio.tv.data.i18n.Strings.get("anisync_test_failure")
            } finally {
                _aniSyncActionBusy.value = null
            }
        }
    }

    fun syncAniSyncNow(service: String, range: String? = null) {
        viewModelScope.launch {
            _aniSyncActionBusy.value = "sync-now"
            _aniSyncMessage.value = null
            try {
                val result = repository.syncAniSyncNow(service = service, limit = 5, range = range)
                if (result.Settings != null) {
                    _aniSyncSettings.value = result.Settings
                }
                _aniSyncMessage.value = result.Message.ifBlank {
                    com.jellio.tv.data.i18n.Strings.format(
                        "anisync_synced_summary",
                        result.SyncedCount,
                        result.FailedCount,
                    )
                }
            } catch (err: Exception) {
                android.util.Log.w("JellioAniSync", "Failed to sync AniSync now", err)
                _aniSyncMessage.value = extractHttpError(err) ?: com.jellio.tv.data.i18n.Strings.get("anisync_sync_failed")
            } finally {
                _aniSyncActionBusy.value = null
            }
        }
    }

    fun startSimklPin(clientId: String?) {
        viewModelScope.launch {
            _aniSyncActionBusy.value = "simkl-pin"
            _aniSyncMessage.value = null
            try {
                val start = repository.startSimklPin(clientId?.takeIf { it.isNotBlank() })
                _simklPinData.value = start
                if (!start.UserCode.isNullOrBlank()) {
                    pollSimklPinLoop(start.UserCode, clientId)
                }
            } catch (err: Exception) {
                android.util.Log.w("JellioAniSync", "Failed to start Simkl PIN", err)
                _aniSyncMessage.value = extractHttpError(err) ?: com.jellio.tv.data.i18n.Strings.get("anisync_simkl_pin_error")
            } finally {
                _aniSyncActionBusy.value = null
            }
        }
    }

    private fun extractHttpError(err: Exception): String? {
        val httpEx = err as? retrofit2.HttpException ?: return null
        val raw = runCatching { httpEx.response()?.errorBody()?.string() }.getOrNull()?.trim()
            ?.trim('"')
            ?.trim()
            ?: return null
        if (raw.isBlank() || raw.startsWith("<") || raw.length > 240) return null
        return raw
    }

    private var simklPollJob: kotlinx.coroutines.Job? = null

    private fun pollSimklPinLoop(userCode: String, clientId: String?) {
        simklPollJob?.cancel()
        simklPollJob = viewModelScope.launch {
            _simklPinPolling.value = true
            val interval = ((_simklPinData.value?.Interval ?: 5).coerceAtLeast(3)) * 1000L
            val maxAttempts = 60
            var attempts = 0
            while (attempts < maxAttempts && _simklPinData.value?.UserCode == userCode) {
                kotlinx.coroutines.delay(interval)
                attempts++
                try {
                    val poll = repository.pollSimklPin(userCode, clientId?.takeIf { it.isNotBlank() })
                    if (poll.Approved) {
                        _simklPinData.value = null
                        _simklPinPolling.value = false
                        if (poll.Settings != null) {
                            _aniSyncSettings.value = poll.Settings
                        } else {
                            loadAniSync()
                        }
                        _aniSyncMessage.value = com.jellio.tv.data.i18n.Strings.get("anisync_connected_success")
                        break
                    }
                    if (!poll.Pending) {
                        // Expired or rejected
                        _simklPinData.value = null
                        _simklPinPolling.value = false
                        _aniSyncMessage.value = poll.Message ?: com.jellio.tv.data.i18n.Strings.get("anisync_simkl_pin_expired")
                        break
                    }
                } catch (err: Exception) {
                    android.util.Log.w("JellioAniSync", "Simkl PIN poll error", err)
                }
            }
            _simklPinPolling.value = false
        }
    }

    fun dismissSimklPin() {
        simklPollJob?.cancel()
        simklPollJob = null
        _simklPinData.value = null
        _simklPinPolling.value = false
    }

    fun clearAniSyncMessage() {
        _aniSyncMessage.value = null
    }
}

