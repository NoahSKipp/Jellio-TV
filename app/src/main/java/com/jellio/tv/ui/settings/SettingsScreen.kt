package com.jellio.tv.ui.settings

import com.jellio.tv.ui.common.dismissOnBack
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.tv.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.Icons
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.border
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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.network.HttpException
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.jellio.tv.BuildConfig
import com.jellio.tv.di.normalizeServerAddress
import com.jellio.tv.ui.perf.MotionLevel
import com.jellio.tv.ui.perf.MotionPolicy
import com.jellio.tv.ui.player.PlayerPrefs
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
import com.jellio.tv.data.prefs.AppLanguage
import com.jellio.tv.ui.i18n.string
import com.jellio.tv.ui.i18n.formatString

private enum class LanguageField { AUDIO, SUBTITLE, DISPLAY }

private enum class SettingsCategory(val labelKey: String, val icon: ImageVector) {
    ACCOUNT("settings_cat_account", Icons.Filled.Person),
    PLAYBACK("settings_cat_playback", Icons.Filled.PlayCircle),
    RECOMMENDATIONS("settings_cat_recommendations", Icons.Filled.AutoAwesome),
    DISPLAY("settings_cat_display", Icons.Filled.Palette),
    ABOUT("settings_cat_about", Icons.Filled.Info);

    val label: String
        @Composable get() = string(labelKey)
}

// A real port of screens/settings.js: this app's own real
// server/account facts, the same real remember-stream-choice
// preference components/streamPicker.js's own picker reads, the real
// default audio/subtitle language preference section, real password
// change (POST Users/{id}/Password) and, only when the server admin
// has not turned the whole real feature off, real Quick Connect
// approval.
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(
    session: Session,
    onLogout: () -> Unit,
    onViewProfile: () -> Unit,
    onShowChangelog: () -> Unit = {},
    onLaunchOnboarding: () -> Unit = {},
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
    var openField by remember { mutableStateOf<LanguageField?>(null) }
    // Real bug found live: AvatarPickerOverlay's own dismiss removed its
    // whole real focused subtree with nothing requesting focus back
    // onto this row first, the same real class of dead-remote bug -
    // declared up here rather than inside the Column below since the
    // overlay handling further down needs it too, outside that Column's
    // own real scope.
    val changeAvatarFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(session.userId) {
        viewModel.load(session)
        viewModel.loadRecommendations(session.userId)
    }

    Box(modifier = modifier.fillMaxSize()) {
    // Categories down the left like the web settings' own menu, the
    // focused one's settings on the right.
    var category by remember { mutableStateOf(SettingsCategory.ACCOUNT) }
    LaunchedEffect(category) { scrollState.scrollTo(0) }
    // OK on a category moves into its settings; Back from the settings
    // returns to the category they belong to, and only Back from the
    // categories leaves the screen.
    val categoryFocus = remember { SettingsCategory.entries.associateWith { FocusRequester() } }
    val firstCategory = categoryFocus.getValue(SettingsCategory.ACCOUNT)
    val contentFocus = remember { FocusRequester() }
    var contentHasFocus by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxSize().padding(top = 32.dp, start = 48.dp, end = 48.dp)) {
        Column(modifier = Modifier.width(280.dp)) {
            Text(text = string("settings_title"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 24.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.focusRestorer(firstCategory),
            ) {
                SettingsCategory.entries.forEach { entry ->
                    SettingsCategoryItem(
                        label = entry.label,
                        icon = entry.icon,
                        selected = entry == category,
                        onFocus = { category = entry },
                        onClick = { runCatching { contentFocus.requestFocus() } },
                        modifier = Modifier.focusRequester(categoryFocus.getValue(entry)),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(48.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .onFocusChanged { contentHasFocus = it.hasFocus }
                .onPreviewKeyEvent { event ->
                    if (event.key != Key.Back || !contentHasFocus) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyUp) runCatching { categoryFocus.getValue(category).requestFocus() }
                    true
                }
                .focusRequester(contentFocus)
                .focusRestorer()
                .verticalScroll(scrollState),
        ) {
          Column(modifier = Modifier.widthIn(max = 820.dp)) {
            Text(text = category.label, style = MaterialTheme.typography.headlineSmall, color = JellioText, modifier = Modifier.padding(top = 4.dp))
            when (category) {
                SettingsCategory.ACCOUNT -> {
                SettingsSection(title = "Profile", icon = Icons.Filled.AccountCircle, description = "Signed in as ${session.userName} on ${session.serverAddress}.") {
                    SettingsActionRow(
                        label = "Change avatar",
                        icon = Icons.Filled.AddAPhoto,
                        onClick = { viewModel.openAvatarPicker() },
                        modifier = Modifier.focusRequester(changeAvatarFocusRequester).onFocusChanged { if (it.isFocused) coroutineScope.launch { scrollState.animateScrollTo(0) } },
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        label = "View profile",
                        description = "Banner, badges and activity.",
                        icon = Icons.Filled.Badge,
                        onClick = onViewProfile,
                        modifier = Modifier.onFocusChanged { if (it.isFocused) coroutineScope.launch { scrollState.animateScrollTo(0) } },
                    )
                }

                val isPrivate by viewModel.isPrivate.collectAsState()
                SettingsSection(title = "Privacy", icon = Icons.Filled.Lock) {
                    SettingsToggleRow(
                        label = "Private profile",
                        description = "Your profile picture and banner stay visible. Achievement badges and activity are hidden from other users.",
                        checked = isPrivate,
                        onToggle = { viewModel.setPrivate(!isPrivate) },
                    )
                }

                SettingsSection(title = "Security", icon = Icons.Filled.Key, form = true) {
                    PasswordSection(session = session, viewModel = viewModel)
                }

                SettingsSection(title = null) {
                    SettingsActionRow(
                        label = "Log Out",
                        icon = Icons.AutoMirrored.Filled.Logout,
                        onClick = onLogout,
                        danger = true,
                    )
                }
                }
                SettingsCategory.PLAYBACK -> {
                SettingsSection(title = "Playback", icon = Icons.Filled.PlayCircle) {
                    SettingsToggleRow(
                        label = "Remember stream choice",
                        description = "Skip the stream picker on a title you already picked one for, for 4 days.",
                        checked = rememberStream,
                        onToggle = { viewModel.setRememberStream(!rememberStream) },
                    )
                    val playerContext = LocalContext.current
                    SettingsDivider()
                    SettingsToggleRow(
                        label = "Auto-skip intros",
                        description = "Jump past an episode's intro by itself when the server knows where it is.",
                        checked = PlayerPrefs.autoSkipIntro,
                        onToggle = { PlayerPrefs.setAutoSkipIntro(playerContext, !PlayerPrefs.autoSkipIntro) },
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        label = "Dialogue Boost / Night Mode",
                        description = "Boosts speech frequencies and compresses dynamic range so quiet dialogue is clear without loud action jumps.",
                        checked = PlayerPrefs.dialogueBoost,
                        onToggle = { PlayerPrefs.setDialogueBoost(playerContext, !PlayerPrefs.dialogueBoost) },
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        label = "Audio passthrough (HDMI)",
                        description = "Pass multichannel audio bitstreams directly to your AV receiver or soundbar without software downmixing.",
                        checked = PlayerPrefs.audioPassthrough,
                        onToggle = { PlayerPrefs.setAudioPassthrough(playerContext, !PlayerPrefs.audioPassthrough) },
                    )
                    SettingsDivider()
                    // Each press steps to the next level.
                    SettingsPickerRow(
                        label = "Pre-buffer",
                        value = when (PlayerPrefs.prebuffer) {
                            "more" -> "More: 3 s to start, loads 90 s ahead, 5 min when paused"
                            "max" -> "Maximum: 6 s to start, loads 3 min ahead, 10 min when paused"
                            else -> "Standard"
                        },
                        onClick = {
                            val next = when (PlayerPrefs.prebuffer) {
                                "standard" -> "more"
                                "more" -> "max"
                                else -> "standard"
                            }
                            PlayerPrefs.setPrebuffer(playerContext, next)
                        },
                    )
                }

                SettingsSection(title = "Language", icon = Icons.Filled.Translate) {
                    SettingsPickerRow(
                        label = string("settings_preferred_audio"),
                        value = audioLanguage?.let { languageName(it) } ?: string("settings_no_preference"),
                        onClick = { openField = LanguageField.AUDIO },
                    )
                    SettingsDivider()
                    SettingsPickerRow(
                        label = string("settings_preferred_subtitle"),
                        value = subtitleLanguage?.let { languageName(it) } ?: string("settings_no_preference"),
                        onClick = { openField = LanguageField.SUBTITLE },
                    )
                }

                SettingsSection(title = "Sleep Timer", icon = Icons.Filled.Bedtime, form = true) {
                    SleepTimerSection(viewModel = viewModel)
                }
                }
                SettingsCategory.RECOMMENDATIONS -> {
                    val communityRowEnabled by viewModel.communityRowEnabled.collectAsState()
                    val communityUsers by viewModel.communityUsers.collectAsState()
                    val excludedUsers by viewModel.excludedUserIds.collectAsState()
                    val allGenres by viewModel.allGenres.collectAsState()
                    val selectedGenres by viewModel.selectedGenres.collectAsState()

                    SettingsSection(
                        title = "What Others Are Watching",
                        icon = Icons.Filled.Group,
                        description = "Show titles currently being watched or recently finished by other users on this server.",
                    ) {
                        SettingsToggleRow(
                            label = "Display row on Home",
                            description = "Show the What Others Are Watching row among your home screen recommendations.",
                            checked = communityRowEnabled,
                            onToggle = { viewModel.setCommunityRowEnabled(session.userId, !communityRowEnabled) },
                        )
                        if (communityRowEnabled && communityUsers.isNotEmpty()) {
                            SettingsDivider()
                            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                                Text(
                                    text = "Included Users",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = JellioText,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                                Text(
                                    text = "Choose whose activity appears in your community row. Toggle off any user to hide their activity.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = JellioTextSecondary,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                                communityUsers.forEach { user ->
                                    val isIncluded = !excludedUsers.contains(user.Id)
                                    val userAvatarUrl = if (!user.PrimaryImageTag.isNullOrEmpty()) {
                                        val cleanServer = com.jellio.tv.di.normalizeServerAddress(session.serverAddress)
                                        "$cleanServer/Users/${user.Id}/Images/Primary?maxWidth=64${if (session.accessToken.isNotEmpty()) "&ApiKey=${session.accessToken}&api_key=${session.accessToken}" else ""}"
                                    } else null

                                    SettingsRowSurface(onClick = { viewModel.toggleCommunityExcludedUser(session.userId, user.Id) }) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(JellioSecondary),
                                        ) {
                                            if (userAvatarUrl != null) {
                                                AsyncImage(
                                                    model = userAvatarUrl,
                                                    contentDescription = user.Name,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize(),
                                                )
                                            } else {
                                                Text(
                                                    text = user.Name.firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                )
                                            }
                                        }
                                        SettingsRowText(label = user.Name, description = null)
                                        SettingsSwitch(checked = isIncluded)
                                    }
                                }
                            }
                        }
                    }

                    SettingsSection(
                        title = "Genre Recommendations",
                        icon = Icons.Filled.MovieFilter,
                        description = "Choose which genre rows appear on your Home screen. When none are selected, rows are chosen automatically based on your library.",
                    ) {
                        SettingsActionRow(
                            label = "Reset to automatic",
                            icon = Icons.Filled.Sync,
                            description = if (selectedGenres.isEmpty()) "Currently using automatic discovery." else "${selectedGenres.size} genres selected. Click to reset to automatic.",
                            onClick = { viewModel.resetSelectedGenres(session.userId) },
                        )
                        if (allGenres.isNotEmpty()) {
                            SettingsDivider()
                            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                                Text(
                                    text = "Available Genres",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = JellioText,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                                allGenres.forEach { genre ->
                                    val isSelected = selectedGenres.contains(genre)
                                    SettingsRowSurface(onClick = { viewModel.toggleSelectedGenre(session.userId, genre) }) {
                                        SettingsRowText(
                                            label = genre,
                                            description = if (isSelected) "Included in Home rows" else null,
                                            color = if (isSelected) JellioSecondary else JellioText,
                                        )
                                        SettingsSwitch(checked = isSelected)
                                    }
                                }
                            }
                        }
                    }
                }
                SettingsCategory.DISPLAY -> {
                SettingsSection(title = string("settings_cat_display"), icon = Icons.Filled.Tv) {
                    val activeLang = com.jellio.tv.data.prefs.DisplayPreferences.displayLanguage
                    SettingsPickerRow(
                        label = string("settings_display_language"),
                        value = "${activeLang.nativeName} (${activeLang.label})",
                        onClick = { openField = LanguageField.DISPLAY },
                    )
                    SettingsDivider()
                    MotionRow()
                    SettingsDivider()
                    val overscan = com.jellio.tv.data.prefs.DisplayPreferences.overscanPercent
                    SettingsPickerRow(
                        label = string("settings_overscan"),
                        value = if (overscan == 0) string("settings_overscan_fullscreen") else formatString("settings_overscan_inset", overscan),
                        onClick = { com.jellio.tv.data.prefs.DisplayPreferences.isCalibrating = true },
                    )
                }
                }
                SettingsCategory.ABOUT -> {
                SettingsSection(title = "About", icon = Icons.Filled.Info, description = "Jellio TV " + BuildConfig.VERSION_NAME) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        SettingsActionRow(
                            label = "What's New",
                            icon = Icons.Filled.AutoAwesome,
                            description = "View new features and improvements in version " + BuildConfig.VERSION_NAME,
                            onClick = onShowChangelog,
                        )
                        SettingsActionRow(
                            label = string("onboarding_wizard_card_title"),
                            icon = Icons.Filled.Tv,
                            description = string("onboarding_wizard_card_desc"),
                            onClick = onLaunchOnboarding,
                        )
                        UpdateCheckRow(viewModel = appUpdateViewModel)
                    }
                }
                }
            }
            Spacer(modifier = Modifier.height(64.dp))
          }
        }
    }

    val field = openField
    if (field == LanguageField.DISPLAY) {
        val currentContext = LocalContext.current
        DisplayLanguagePickerOverlay(
            selectedLanguage = com.jellio.tv.data.prefs.DisplayPreferences.displayLanguage,
            onSelect = { lang ->
                com.jellio.tv.data.prefs.DisplayPreferences.setDisplayLanguage(currentContext, lang)
                openField = null
            },
            onDismiss = { openField = null },
        )
    } else if (field != null) {
        val selectedCode = if (field == LanguageField.AUDIO) audioLanguage else subtitleLanguage
        LanguagePickerOverlay(
            title = if (field == LanguageField.AUDIO) string("settings_preferred_audio") else string("settings_preferred_subtitle"),
            selectedCode = selectedCode,
            onSelect = { code ->
                if (field == LanguageField.AUDIO) viewModel.setAudioLanguage(session, code) else viewModel.setSubtitleLanguage(session, code)
                openField = null
            },
            onDismiss = { openField = null },
        )
    }

    val showAvatarPicker by viewModel.showAvatarPicker.collectAsState()
    // Real bug found live: selecting a preset/finishing an upload
    // closes this overlay from inside SettingsViewModel itself, once
    // its own real network call settles - bypassing this composable's
    // own onDismiss wrapper below entirely, the exact same real dead-
    // remote class every other overlay in this app already had to fix.
    // Watching the real flag itself instead answers every real path
    // that can flip it false, not just the one this screen's own
    // Surface directly calls.
    var wasShowingAvatarPicker by remember { mutableStateOf(false) }
    LaunchedEffect(showAvatarPicker) {
        if (wasShowingAvatarPicker && !showAvatarPicker) changeAvatarFocusRequester.requestFocus()
        wasShowingAvatarPicker = showAvatarPicker
    }
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
    SettingsRowSurface(onClick = onClick) {
        SettingsRowText(label = label, description = value)
        Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = null, tint = JellioTextSecondary, modifier = Modifier.size(24.dp))
    }
}

// Mirrors screens/settings.js's own real language pickers, docked
// right the same way the player's own subtitle popover already is:
// "No preference" first, then every real LANGUAGE_OPTIONS entry,
// saving on selection rather than needing a separate confirm step.
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DisplayLanguagePickerOverlay(
    selectedLanguage: com.jellio.tv.data.prefs.AppLanguage,
    onSelect: (com.jellio.tv.data.prefs.AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val firstRowFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstRowFocusRequester.requestFocus() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .dismissOnBack(onDismiss)
            .focusProperties { onExit = { FocusRequester.Cancel } }
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
                .padding(top = 48.dp, bottom = 48.dp),
        ) {
            Text(
                text = string("settings_display_language"),
                color = JellioText,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            )
            LazyColumn {
                items(com.jellio.tv.data.prefs.AppLanguage.entries) { lang ->
                    LanguagePickerRow(
                        label = "${lang.nativeName} (${lang.label})",
                        isSelected = lang == selectedLanguage,
                        onClick = { onSelect(lang) },
                        focusRequester = if (lang == com.jellio.tv.data.prefs.AppLanguage.entries.first()) firstRowFocusRequester else null,
                    )
                }
            }
        }
    }
}
@OptIn(ExperimentalComposeUiApi::class)
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
            .dismissOnBack(onDismiss)
            .focusProperties { onExit = { FocusRequester.Cancel } }
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
            focusedContainerColor = Color.White.copy(alpha = 0.28f),
            focusedContentColor = JellioText,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(8.dp),
            )
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
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
    // Folded behind a button: open, the three fields each grabbed the
    // D-pad and brought up the keyboard on the way down the page.
    var open by remember { mutableStateOf(false) }
    var everOpened by remember { mutableStateOf(false) }
    val firstField = remember { androidx.compose.ui.focus.FocusRequester() }
    val openButton = remember { androidx.compose.ui.focus.FocusRequester() }
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
            open = false
        }
    }
    LaunchedEffect(open) {
        if (open) {
            everOpened = true
            runCatching { firstField.requestFocus() }
        } else if (everOpened) {
            runCatching { openButton.requestFocus() }
        }
    }

    Column {
        if (!open) {
            status?.let { Text(text = it, color = JellioTextSecondary, modifier = Modifier.padding(bottom = 12.dp)) }
            PasswordButton(label = "Change password", onClick = { open = true }, modifier = Modifier.focusRequester(openButton))
            return@Column
        }
        JellioTextField(value = current, onValueChange = { current = it }, label = "Current password", isPassword = true, modifier = Modifier.fillMaxWidth().focusRequester(firstField))
        Spacer(modifier = Modifier.height(12.dp))
        JellioTextField(value = next, onValueChange = { next = it }, label = "New password", isPassword = true, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(12.dp))
        JellioTextField(value = confirm, onValueChange = { confirm = it }, label = "Confirm new password", isPassword = true, modifier = Modifier.fillMaxWidth())
        status?.let { Text(text = it, color = JellioTextSecondary, modifier = Modifier.padding(top = 12.dp)) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 16.dp)) {
            PasswordButton(
                label = if (isUpdating) "Updating..." else "Update password",
                enabled = !isUpdating,
                onClick = { viewModel.updatePassword(session, current, next, confirm) },
            )
            PasswordButton(
                label = "Cancel",
                enabled = !isUpdating,
                onClick = {
                    current = ""
                    next = ""
                    confirm = ""
                    open = false
                },
            )
        }
    }
}

@Composable
private fun PasswordButton(label: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = JellioBgElevated,
            contentColor = JellioText,
            focusedContainerColor = Color.White.copy(alpha = 0.28f),
            focusedContentColor = JellioText,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(999.dp),
            )
        ),
        modifier = modifier,
    ) {
        Text(text = label, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
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
                colors = ClickableSurfaceDefaults.colors(containerColor = JellioBgElevated, contentColor = JellioText, focusedContainerColor = Color.White.copy(alpha = 0.18f), focusedContentColor = JellioText),
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text(text = if (isCancelling) "Cancelling..." else "Cancel timer", modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
            }
        }
    }
}

@Composable
private fun SettingsCategoryItem(label: String, icon: ImageVector, selected: Boolean, onFocus: () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White.copy(alpha = 0.10f) else Color.Transparent,
            contentColor = if (selected) JellioText else JellioTextSecondary,
            focusedContainerColor = Color.White.copy(alpha = 0.16f),
            focusedContentColor = JellioText,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, JellioSecondary), shape = RoundedCornerShape(12.dp)),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) onFocus() },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(text = label, style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false)
        }
    }
}

private val CardBorder = Color.White.copy(alpha = 0.08f)
private val CardBg = Color.White.copy(alpha = 0.035f)
private val DangerColor = Color(0xFFFF6B81)

// A grouped card like the web settings: an icon badge and title over
// divided rows. form = true pads the body for fields and buttons.
@Composable
private fun SettingsSection(
    title: String?,
    icon: ImageVector? = null,
    description: String? = null,
    form: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(top = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
    ) {
        if (title != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                if (icon != null) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.06f)),
                    ) {
                        Icon(imageVector = icon, contentDescription = null, tint = JellioText, modifier = Modifier.size(22.dp))
                    }
                }
                Column {
                    Text(text = title, style = MaterialTheme.typography.titleMedium, color = JellioText)
                    if (description != null) {
                        Text(text = description, style = MaterialTheme.typography.bodySmall, color = JellioTextSecondary)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(CardBorder))
        }
        Column(modifier = if (form) Modifier.padding(20.dp) else Modifier.padding(vertical = 6.dp)) { content() }
    }
}

@Composable
private fun SettingsDivider() {
    Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(1.dp).background(CardBorder))
}

// One focusable row inside a card: a soft highlight with a thin accent
// border when focused, nothing at rest.
@Composable
private fun SettingsRowSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val accent = if (danger) DangerColor else JellioSecondary
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = JellioText,
            focusedContainerColor = if (danger) DangerColor.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.08f),
            focusedContentColor = JellioText,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, accent), shape = RoundedCornerShape(12.dp)),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
            content = content,
        )
    }
}

@Composable
private fun RowScope.SettingsRowText(label: String, description: String?, color: Color = JellioText) {
    Column(modifier = Modifier.weight(1f)) {
        Text(text = label, color = color, style = MaterialTheme.typography.titleSmall)
        if (!description.isNullOrBlank()) {
            Text(text = description, color = JellioTextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SettingsActionRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    danger: Boolean = false,
) {
    val color = if (danger) DangerColor else JellioText
    SettingsRowSurface(onClick = onClick, modifier = modifier, danger = danger) {
        Icon(imageVector = icon, contentDescription = null, tint = if (danger) DangerColor else JellioTextSecondary, modifier = Modifier.size(22.dp))
        SettingsRowText(label = label, description = description, color = color)
        if (!danger) {
            Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = null, tint = JellioTextSecondary, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp)) {
        Text(text = label, color = JellioTextSecondary, style = MaterialTheme.typography.bodySmall)
        Text(text = value, color = JellioText, style = MaterialTheme.typography.bodyLarge)
    }
}

// The web settings' toggle: a pill track with a sliding thumb.
@Composable
private fun SettingsSwitch(checked: Boolean) {
    Box(
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        modifier = Modifier
            .width(48.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (checked) JellioSecondary else Color.White.copy(alpha = 0.14f))
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) JellioBg else JellioText),
        )
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
            colors = ClickableSurfaceDefaults.colors(
                containerColor = JellioBgElevated,
                contentColor = JellioText,
                focusedContainerColor = Color.White.copy(alpha = 0.28f),
                focusedContentColor = JellioText,
            ),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(2.dp, Color.White),
                    shape = RoundedCornerShape(999.dp),
                )
            ),
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
                    colors = com.jellio.tv.ui.common.primaryButtonColors(),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(2.5.dp, Color.White),
                            shape = RoundedCornerShape(999.dp),
                        )
                    ),
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

// Seasonal animations: Auto picks from the device check, the rest force
// it. Pressing cycles through them.
@Composable
private fun MotionRow() {
    val context = LocalContext.current
    val preference = MotionPolicy.preference
    val profile = MotionPolicy.profile
    val options = listOf("auto", "full", "reduced", "still", "off")
    fun labelOf(value: String) = when (value) {
        "full" -> "Full"
        "reduced" -> "Reduced"
        "still" -> "Still (no animation)"
        "off" -> "Off"
        else -> "Auto (" + when (profile?.suggested) {
            MotionLevel.REDUCED -> "Reduced"
            MotionLevel.STILL -> "Still"
            MotionLevel.OFF -> "Off"
            else -> "Full"
        } + ")"
    }
    SettingsRowSurface(
        onClick = {
            val next = options[(options.indexOf(preference).coerceAtLeast(0) + 1) % options.size]
            MotionPolicy.setPreference(context, next)
        },
    ) {
        SettingsRowText(
            label = "Seasonal animations: " + labelOf(preference),
            description = "Reduced redraws less often and keeps menus smoother on slower devices. " +
                (profile?.let { "This device: " + it.describe() + "." } ?: ""),
        )
        Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = null, tint = JellioTextSecondary, modifier = Modifier.size(24.dp))
    }
}

// tv-material3 ships no Switch component reliably verified in this
// codebase's own pinned version (the same real lesson
// CircularProgressIndicator's own real CI failure already taught this
// session): a plain clickable row toggling an On/Off label needs no
// such trust.
@Composable
private fun SettingsToggleRow(label: String, description: String, checked: Boolean, onToggle: () -> Unit) {
    SettingsRowSurface(onClick = onToggle) {
        SettingsRowText(label = label, description = description)
        SettingsSwitch(checked = checked)
    }
}
