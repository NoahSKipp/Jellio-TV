package com.jellio.tv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
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

    private val _quickConnectEnabled = MutableStateFlow(false)
    val quickConnectEnabled: StateFlow<Boolean> = _quickConnectEnabled.asStateFlow()

    private val _isAuthorizingQuickConnect = MutableStateFlow(false)
    val isAuthorizingQuickConnect: StateFlow<Boolean> = _isAuthorizingQuickConnect.asStateFlow()

    private val _quickConnectStatus = MutableStateFlow<String?>(null)
    val quickConnectStatus: StateFlow<String?> = _quickConnectStatus.asStateFlow()

    private val _quickConnectApproveTick = MutableStateFlow(0)
    val quickConnectApproveTick: StateFlow<Int> = _quickConnectApproveTick.asStateFlow()

    private var loadedUserId: String? = null

    init {
        viewModelScope.launch { _rememberStream.value = streamPreferences.isRememberEnabled() }
        viewModelScope.launch { _quickConnectEnabled.value = repository.isQuickConnectEnabled() }
    }

    fun setRememberStream(enabled: Boolean) {
        _rememberStream.value = enabled
        viewModelScope.launch { streamPreferences.setRememberEnabled(enabled) }
    }

    fun load(session: Session) {
        if (loadedUserId == session.userId) return
        loadedUserId = session.userId
        viewModelScope.launch {
            runCatching { repository.getUser(session.userId).Configuration }.getOrNull()?.let { configuration ->
                _audioLanguage.value = matchLanguageOption(configuration.AudioLanguagePreference)?.code
                _subtitleLanguage.value = matchLanguageOption(configuration.SubtitleLanguagePreference)?.code
            }
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

    fun authorizeQuickConnect(code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            _isAuthorizingQuickConnect.value = true
            _quickConnectStatus.value = "Approving…"
            try {
                val authorized = repository.authorizeQuickConnect(trimmed)
                _quickConnectStatus.value = if (authorized) "Device approved." else "That code was not recognized."
                if (authorized) _quickConnectApproveTick.value += 1
            } catch (err: Exception) {
                _quickConnectStatus.value = "Could not approve that code."
            } finally {
                _isAuthorizingQuickConnect.value = false
            }
        }
    }
}
