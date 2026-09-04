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
) : ViewModel() {

    private val _rememberStream = MutableStateFlow(true)
    val rememberStream: StateFlow<Boolean> = _rememberStream.asStateFlow()

    // Real codes, or null for "No preference", matching the currently
    // saved UserDto.Configuration.AudioLanguagePreference/
    // SubtitleLanguagePreference exactly.
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

    // Incremented only on a real successful password update, the
    // screen's own signal to clear its three local text fields
    // (mirrors screens/settings.js's own form.reset() on success).
    private val _passwordUpdateTick = MutableStateFlow(0)
    val passwordUpdateTick: StateFlow<Int> = _passwordUpdateTick.asStateFlow()

    // Real port of screens/settings.js's own buildSleepTimerSection():
    // real cross-client status (a timer started from the player on
    // this same account/device pair) surfaced here too, for a reader
    // not currently on the player screen. "No active playback
    // session." is the same real placeholder that file's own status
    // paragraph starts with before the real fetch below ever resolves,
    // left in place on a failed fetch the same way that function's own
    // empty catch block does, never overwritten with an error message.
    private val _sleepTimerStatusText = MutableStateFlow("No active playback session.")
    val sleepTimerStatusText: StateFlow<String> = _sleepTimerStatusText.asStateFlow()

    private val _sleepTimerActive = MutableStateFlow(false)
    val sleepTimerActive: StateFlow<Boolean> = _sleepTimerActive.asStateFlow()

    private val _isCancellingSleepTimer = MutableStateFlow(false)
    val isCancellingSleepTimer: StateFlow<Boolean> = _isCancellingSleepTimer.asStateFlow()

    // Real port of components/avatarPicker.js's own openAvatarPicker()
    // state: presets re-fetched fresh every real open (that file's own
    // real getAvatarPresets() call, no caching), a single busy key
    // rather than a per-tile boolean list since only one real upload
    // or preset-set request is ever in flight at once, same real
    // reasoning that file's own option.disabled/uploadOption.disabled
    // toggle only ever the one real tile clicked.
    private val _showAvatarPicker = MutableStateFlow(false)
    val showAvatarPicker: StateFlow<Boolean> = _showAvatarPicker.asStateFlow()

    private val _isLoadingAvatarPresets = MutableStateFlow(false)
    val isLoadingAvatarPresets: StateFlow<Boolean> = _isLoadingAvatarPresets.asStateFlow()

    private val _avatarPresets = MutableStateFlow<List<AvatarPresetDto>>(emptyList())
    val avatarPresets: StateFlow<List<AvatarPresetDto>> = _avatarPresets.asStateFlow()

    private val _avatarPickerStatus = MutableStateFlow<String?>(null)
    val avatarPickerStatus: StateFlow<String?> = _avatarPickerStatus.asStateFlow()

    // Null when nothing is in flight, "upload" for the upload tile, or
    // a real preset's own Id for that tile.
    private val _avatarBusyKey = MutableStateFlow<String?>(null)
    val avatarBusyKey: StateFlow<String?> = _avatarBusyKey.asStateFlow()

    // Real port of screens/settings.js's own buildPrivacyCard(): badges
    // and activity go dark for other users, the profile picture and
    // banner stay visible either way, same real Steam-style split
    // ProfileScreen.kt's own header already documents.
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
                _subtitleLanguage.value = matchLanguageOption(configuration.SubtitleLanguagePreference)?.code
            }
        }
        // Real screens/settings.js's own async loaded Privacy card:
        // fired alongside the user fetch above, not blocking it.
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
        val previous = _audioLanguage.value
        _audioLanguage.value = code
        saveLanguagePreferences(session, code, _subtitleLanguage.value) { _audioLanguage.value = previous }
    }

    fun setSubtitleLanguage(session: Session, code: String?) {
        val previous = _subtitleLanguage.value
        _subtitleLanguage.value = code
        saveLanguagePreferences(session, _audioLanguage.value, code) { _subtitleLanguage.value = previous }
    }

    private fun saveLanguagePreferences(session: Session, audio: String?, subtitle: String?, onFailure: () -> Unit) {
        viewModelScope.launch {
            _languageStatus.value = "Saving…"
            try {
                repository.updateLanguagePreferences(session.userId, audio, subtitle)
                _languageStatus.value = "Saved, takes effect the next time you start playback."
            } catch (err: Exception) {
                onFailure()
                _languageStatus.value = "Could not save language preferences."
            }
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

    // Real port of that file's own Cancel timer click handler: on
    // success the status line and button both react (Compose drops the
    // button itself once sleepTimerActive flips false, the same real
    // job that function's own cancel.remove() does); on failure only
    // the button re-enables, the "A sleep timer is running." text and
    // button both left in place for a retry, same real fallback that
    // function's own catch block leaves too.
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

    // Real port of components/avatarPicker.js's own openAvatarPicker():
    // presets fetched fresh on every real open, an empty real result
    // getting the exact same real "upload your own instead" status
    // that function's own early return shows.
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

    // Real port of that file's own file input change handler: a real
    // file the reader picked off their own device, Jellyfin already
    // supports this natively (an animated gif included), real feedback
    // asked directly for a way to reach it rather than presets only.
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
}
