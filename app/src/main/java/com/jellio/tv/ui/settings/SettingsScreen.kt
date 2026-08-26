package com.jellio.tv.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jellio.tv.BuildConfig
import com.jellio.tv.data.model.LanguageOption
import com.jellio.tv.data.model.LANGUAGE_OPTIONS
import com.jellio.tv.data.model.languageName
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.common.JellioTextField
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import com.jellio.tv.ui.update.AppUpdateViewModel
import com.jellio.tv.ui.update.ManualCheckResult

private enum class LanguageField { AUDIO, SUBTITLE }

// A real port of screens/settings.js: this app's own real
// server/account facts, the same real remember-stream-choice
// preference components/streamPicker.js's own picker reads, the real
// default audio/subtitle language preference section, real password
// change (POST Users/{id}/Password) and, only when the server admin
// has not turned the whole real feature off, real Quick Connect
// approval.
@Composable
fun SettingsScreen(
    session: Session,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    // AppBootGate's own hiltViewModel() call already checks once per
    // real app open; this resolves to that exact same Activity-scoped
    // instance for the manual "Check for Updates" button below.
    appUpdateViewModel: AppUpdateViewModel = hiltViewModel(),
) {
    val rememberStream by viewModel.rememberStream.collectAsState()
    val audioLanguage by viewModel.audioLanguage.collectAsState()
    val subtitleLanguage by viewModel.subtitleLanguage.collectAsState()
    val isAdministrator by viewModel.isAdministrator.collectAsState()
    var openField by remember { mutableStateOf<LanguageField?>(null) }
    val context = LocalContext.current

    LaunchedEffect(session.userId) { viewModel.load(session) }

    Box(modifier = modifier.fillMaxSize()) {
    // Real bug found live testing on device: this Column carried no
    // scroll of its own at all, so once Change Password/Sleep
    // Timer/Quick Connect/About (the Check for Updates button among
    // them) pushed the real content taller than one screen, everything
    // past that point was simply unreachable, nothing to bring it into
    // view. verticalScroll() is Compose's own real fix, the same real
    // bring-into-view behaviour a LazyColumn gets for free already
    // covering a plain Column here too.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 32.dp, start = 48.dp, end = 48.dp),
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.titleLarge)

        SettingsSection(title = "Server") {
            SettingsRow(label = "Address", value = session.serverAddress)
            SettingsRow(label = "Signed in as", value = session.userName)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
                Surface(
                    onClick = { viewModel.openAvatarPicker() },
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = JellioBgElevated),
                ) {
                    Text(text = "Change avatar", modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
                }
                // Real screens/settings.js's own IsAdministrator gated
                // "Open admin dashboard" button: that file's own click
                // handler just moves the already loaded jellyfin-web
                // page's own hash onto #/dashboard, real native chrome
                // for it already loaded underneath. This app embeds no
                // native jellyfin-web page to fall through to for that,
                // so a device browser opens the same real dashboard
                // route fresh instead (SettingsViewModel.kt's own
                // adminDashboardUrl header comment).
                if (isAdministrator) {
                    Surface(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(viewModel.adminDashboardUrl(session)))
                            context.startActivity(intent)
                        },
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                        colors = ClickableSurfaceDefaults.colors(containerColor = JellioBgElevated),
                    ) {
                        Text(text = "Open admin dashboard", modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
                    }
                }
            }
        }

        SettingsSection(title = "Playback") {
            SettingsToggleRow(
                label = "Remember stream choice",
                description = "Skip the stream picker on a title you already picked one for, for 4 days.",
                checked = rememberStream,
                onToggle = { viewModel.setRememberStream(!rememberStream) },
            )
        }

        SettingsSection(title = "Language") {
            SettingsPickerRow(
                label = "Default audio language",
                value = audioLanguage?.let { languageName(it) } ?: "No preference",
                onClick = { openField = LanguageField.AUDIO },
            )
            SettingsPickerRow(
                label = "Default subtitle language",
                value = subtitleLanguage?.let { languageName(it) } ?: "No preference",
                onClick = { openField = LanguageField.SUBTITLE },
            )
        }

        SettingsSection(title = "Change Password") {
            PasswordSection(session = session, viewModel = viewModel)
        }

        SettingsSection(title = "Sleep Timer") {
            SleepTimerSection(viewModel = viewModel)
        }

        val quickConnectEnabled by viewModel.quickConnectEnabled.collectAsState()
        if (quickConnectEnabled) {
            SettingsSection(title = "Quick Connect") {
                QuickConnectSection(viewModel = viewModel)
            }
        }

        SettingsSection(title = "About") {
            SettingsRow(label = "Version", value = BuildConfig.VERSION_NAME)
            UpdateCheckRow(viewModel = appUpdateViewModel)
        }

        Surface(
            onClick = onLogout,
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = JellioSecondary, contentColor = JellioBg),
            modifier = Modifier.padding(top = 32.dp),
        ) {
            Text(text = "Log Out", modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp))
        }
        Spacer(modifier = Modifier.height(64.dp))
    }

    val field = openField
    if (field != null) {
        val selectedCode = if (field == LanguageField.AUDIO) audioLanguage else subtitleLanguage
        LanguagePickerOverlay(
            title = if (field == LanguageField.AUDIO) "Default audio language" else "Default subtitle language",
            selectedCode = selectedCode,
            onSelect = { code ->
                if (field == LanguageField.AUDIO) viewModel.setAudioLanguage(session, code) else viewModel.setSubtitleLanguage(session, code)
                openField = null
            },
            onDismiss = { openField = null },
        )
    }

    val showAvatarPicker by viewModel.showAvatarPicker.collectAsState()
    if (showAvatarPicker) {
        val avatarPresets by viewModel.avatarPresets.collectAsState()
        val avatarStatus by viewModel.avatarPickerStatus.collectAsState()
        val avatarBusyKey by viewModel.avatarBusyKey.collectAsState()
        AvatarPickerOverlay(
            presets = avatarPresets,
            status = avatarStatus,
            busyKey = avatarBusyKey,
            presetImageUrl = { id -> viewModel.avatarPresetUrl(session, id) },
            onSelectPreset = { id -> viewModel.selectAvatarPreset(session, id) },
            onUpload = { bytes, contentType -> viewModel.uploadAvatar(session, bytes, contentType) },
            onDismiss = { viewModel.closeAvatarPicker() },
        )
    }
    }
}

@Composable
private fun SettingsPickerRow(label: String, value: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = JellioBgElevated),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, color = JellioText)
            Text(text = value, color = JellioTextSecondary)
        }
    }
}

// Mirrors screens/settings.js's own real language pickers, docked
// right the same way the player's own subtitle popover already is:
// "No preference" first, then every real LANGUAGE_OPTIONS entry,
// saving on selection rather than needing a separate confirm step.
@Composable
private fun LanguagePickerOverlay(
    title: String,
    selectedCode: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    // Real bug found live testing on device: nothing here ever claimed
    // focus on open (this LazyColumn carried no focusRestorer() either,
    // the same real gap HomeScreen's own header already documents for
    // a plain Compose Foundation list), so the very first D-pad press
    // after opening this had no defined target at all. Same real
    // pattern LibraryPickerOverlay.kt's own firstEntryFocusRequester
    // already uses: a requester on the first real row, claimed the
    // moment this composes.
    val firstRowFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstRowFocusRequester.requestFocus() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .fillMaxSize()
                .background(JellioBgElevated)
                .padding(top = 48.dp, start = 0.dp, end = 0.dp, bottom = 48.dp),
        ) {
            Text(
                text = title,
                color = JellioText,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            )
            LazyColumn {
                item {
                    LanguagePickerRow(
                        label = "No preference",
                        isSelected = selectedCode == null,
                        onClick = { onSelect(null) },
                        focusRequester = firstRowFocusRequester,
                    )
                }
                items(LANGUAGE_OPTIONS) { option: LanguageOption ->
                    LanguagePickerRow(label = option.name, isSelected = selectedCode == option.code, onClick = { onSelect(option.code) })
                }
            }
        }
    }
}

@Composable
private fun LanguagePickerRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) Color.White.copy(alpha = 0.18f) else Color.Transparent,
            contentColor = JellioText,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).let {
            if (focusRequester != null) it.focusRequester(focusRequester) else it
        },
    ) {
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
        )
    }
}

// Mirrors screens/settings.js's own buildPasswordSection(): POST
// Users/{id}/Password, same real CurrentPw/NewPw body shape, clearing
// its own three fields only on a real successful update rather than
// after every submit attempt.
@Composable
private fun PasswordSection(session: Session, viewModel: SettingsViewModel) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val isUpdating by viewModel.isUpdatingPassword.collectAsState()
    val status by viewModel.passwordStatus.collectAsState()
    val tick by viewModel.passwordUpdateTick.collectAsState()

    LaunchedEffect(tick) {
        if (tick > 0) {
            current = ""
            next = ""
            confirm = ""
        }
    }

    Column {
        JellioTextField(value = current, onValueChange = { current = it }, label = "Current password", isPassword = true, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(12.dp))
        JellioTextField(value = next, onValueChange = { next = it }, label = "New password", isPassword = true, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(12.dp))
        JellioTextField(value = confirm, onValueChange = { confirm = it }, label = "Confirm new password", isPassword = true, modifier = Modifier.fillMaxWidth())
        status?.let { Text(text = it, color = JellioTextSecondary, modifier = Modifier.padding(top = 12.dp)) }
        Surface(
            onClick = { viewModel.updatePassword(session, current, next, confirm) },
            enabled = !isUpdating,
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = JellioBgElevated),
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(text = if (isUpdating) "Updating..." else "Update password", modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
        }
    }
}

// Mirrors screens/settings.js's own buildQuickConnectSection(): only
// ever shown when GET QuickConnect/Enabled says the server admin has
// not turned the whole real feature off, POST QuickConnect/Authorize
// approving a real pending request another device started.
@Composable
private fun QuickConnectSection(viewModel: SettingsViewModel) {
    var code by remember { mutableStateOf("") }
    val isAuthorizing by viewModel.isAuthorizingQuickConnect.collectAsState()
    val status by viewModel.quickConnectStatus.collectAsState()
    val tick by viewModel.quickConnectApproveTick.collectAsState()

    LaunchedEffect(tick) {
        if (tick > 0) code = ""
    }

    Column {
        Text(text = "Approve a sign in on another device using its own real code.", color = JellioTextSecondary)
        Spacer(modifier = Modifier.height(12.dp))
        JellioTextField(value = code, onValueChange = { code = it }, label = "Code shown on the other device", modifier = Modifier.fillMaxWidth())
        status?.let { Text(text = it, color = JellioTextSecondary, modifier = Modifier.padding(top = 12.dp)) }
        Surface(
            onClick = { viewModel.authorizeQuickConnect(code) },
            enabled = !isAuthorizing,
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = JellioBgElevated),
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(text = if (isAuthorizing) "Approving..." else "Approve", modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
        }
    }
}

// Mirrors screens/settings.js's own buildSleepTimerSection(): real
// cross-client status for a timer started from the player on this
// same account/device pair, surfaced here for a reader not currently
// on the player screen. Only the status line shows while no timer is
// running; the Cancel timer button only ever renders alongside it,
// same real condition that file's own real.Active check gates both on.
@Composable
private fun SleepTimerSection(viewModel: SettingsViewModel) {
    val statusText by viewModel.sleepTimerStatusText.collectAsState()
    val active by viewModel.sleepTimerActive.collectAsState()
    val isCancelling by viewModel.isCancellingSleepTimer.collectAsState()

    Column {
        Text(text = statusText, color = JellioTextSecondary)
        if (active) {
            Surface(
                onClick = { viewModel.cancelSleepTimer() },
                enabled = !isCancelling,
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                colors = ClickableSurfaceDefaults.colors(containerColor = JellioBgElevated),
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text(text = if (isCancelling) "Cancelling..." else "Cancel timer", modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(top = 32.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = JellioTextSecondary)
        Column(modifier = Modifier.padding(top = 12.dp)) { content() }
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Box(modifier = Modifier.padding(vertical = 8.dp)) {
        Column {
            Text(text = label, color = JellioTextSecondary)
            Text(text = value, color = JellioText, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

// AppUpdateViewModel's own manualCheckResult, real feedback asked for
// alongside the unprompted UpdateToast that only ever appears on an
// actual find: a real button here always answers back, up to date or
// not, rather than leaving a reader who tapped it with no idea
// whether the check even ran.
@Composable
private fun UpdateCheckRow(viewModel: AppUpdateViewModel) {
    val result by viewModel.manualCheckResult.collectAsState()
    val updateState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.padding(top = 12.dp)) {
        Surface(
            onClick = { viewModel.checkForUpdateManually() },
            enabled = result != ManualCheckResult.Checking,
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = JellioBgElevated),
        ) {
            Text(text = "Check for Updates", modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
        }
        when (val current = result) {
            null -> {}
            ManualCheckResult.Checking -> {
                Text(text = "Checking...", color = JellioTextSecondary, modifier = Modifier.padding(top = 10.dp))
            }
            ManualCheckResult.UpToDate -> {
                Text(text = "You're on the latest version.", color = JellioTextSecondary, modifier = Modifier.padding(top = 10.dp))
            }
            ManualCheckResult.Error -> {
                Text(text = "Couldn't check for updates.", color = JellioTextSecondary, modifier = Modifier.padding(top = 10.dp))
            }
            is ManualCheckResult.UpdateFound -> {
                Text(
                    text = "Update available: ${current.version}",
                    color = JellioText,
                    modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
                )
                Surface(
                    onClick = { viewModel.download() },
                    enabled = !updateState.downloading,
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = JellioSecondary, contentColor = JellioBg),
                ) {
                    Text(
                        text = if (updateState.downloading) "Downloading..." else "Install",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

// tv-material3 ships no Switch component reliably verified in this
// codebase's own pinned version (the same real lesson
// CircularProgressIndicator's own real CI failure already taught this
// session): a plain clickable row toggling an On/Off label needs no
// such trust.
@Composable
private fun SettingsToggleRow(label: String, description: String, checked: Boolean, onToggle: () -> Unit) {
    Surface(
        onClick = onToggle,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = JellioBgElevated),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.padding(end = 16.dp)) {
                Text(text = label, color = JellioText)
                Text(text = description, color = JellioTextSecondary, style = MaterialTheme.typography.bodyLarge)
            }
            Text(text = if (checked) "On" else "Off", color = if (checked) JellioText else JellioTextSecondary)
        }
    }
}
