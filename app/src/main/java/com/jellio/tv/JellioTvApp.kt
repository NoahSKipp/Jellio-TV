package com.jellio.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.repeatOnLifecycle
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.AppViewModel
import com.jellio.tv.ui.AuthState
import com.jellio.tv.ui.PlayAction
import com.jellio.tv.ui.auth.LoginScreen
import com.jellio.tv.ui.detail.DetailScreen
import com.jellio.tv.ui.detail.StreamPickerOverlay
import com.jellio.tv.ui.feed.FeedScreen
import com.jellio.tv.ui.friends.FriendsViewModel
import com.jellio.tv.ui.friends.badgeVisible
import com.jellio.tv.ui.home.HomeScreen
import com.jellio.tv.ui.home.HomeViewModel
import com.jellio.tv.ui.library.LibraryScreen
import com.jellio.tv.ui.library.LibraryViewModel
import com.jellio.tv.ui.nav.AccountSwitcherOverlay
import com.jellio.tv.ui.nav.JellioRoute
import com.jellio.tv.ui.nav.SidebarNav
import com.jellio.tv.ui.nav.SidebarReservedWidth
import com.jellio.tv.ui.nav.isImmersive
import com.jellio.tv.ui.nav.jellioNavItems
import com.jellio.tv.ui.nowplaying.QuickHubTab
import com.jellio.tv.ui.person.PersonScreen
import com.jellio.tv.ui.player.PlayerScreen
import com.jellio.tv.ui.profile.ProfileScreen
import com.jellio.tv.ui.search.SearchScreen
import com.jellio.tv.ui.seasonal.SeasonalEffectsOverlay
import com.jellio.tv.ui.seasonal.SeasonalEffectsViewModel
import com.jellio.tv.ui.seasonal.SeasonalSkyOverlay
import com.jellio.tv.ui.service.ServiceScreen
import com.jellio.tv.ui.settings.SettingsScreen
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.scaled
import com.jellio.tv.ui.update.AppUpdateViewModel
import com.jellio.tv.ui.update.BootSplashMark
import com.jellio.tv.ui.update.UpdateToast
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
internal fun JellioTvRoot(
    deepLinkItemId: String?,
    onDeepLinkConsumed: () -> Unit,
    onPlayerActiveChanged: (Boolean) -> Unit = {},
    appViewModel: AppViewModel = hiltViewModel(),
    appUpdateViewModel: AppUpdateViewModel = hiltViewModel(),
) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            appUpdateViewModel.checkPeriodically()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = appViewModel.authState.collectAsState().value) {
            AuthState.Loading -> Box(Modifier.fillMaxSize()) {}
            AuthState.LoggedOut -> LoginScreen(modifier = Modifier.fillMaxSize())
            is AuthState.LoggedIn -> AppBootGate(
                session = state.session,
                appViewModel = appViewModel,
                deepLinkItemId = deepLinkItemId,
                onDeepLinkConsumed = onDeepLinkConsumed,
                onPlayerActiveChanged = onPlayerActiveChanged,
            )
        }

        val updateState by appUpdateViewModel.uiState.collectAsState()
        // Held back during playback, the prompt takes focus; it shows
        // once the player closes.
        updateState.availableVersion?.takeUnless { com.jellio.tv.ui.player.PlayerPresence.active }?.let { version ->
            UpdateToast(
                version = version,
                downloading = updateState.downloading,
                onDownload = { appUpdateViewModel.download() },
                onDismiss = { appUpdateViewModel.dismiss() },
                modifier = Modifier.fillMaxSize(),
                error = updateState.error,
            )
        }
    }
}

private const val SPLASH_MIN_MS = 350L
private const val SPLASH_MAX_MS = 1_200L
private const val FRIENDS_POLL_MS = 60_000L

@Composable
private fun AppBootGate(
    session: Session,
    appViewModel: AppViewModel,
    deepLinkItemId: String?,
    onDeepLinkConsumed: () -> Unit,
    onPlayerActiveChanged: (Boolean) -> Unit = {},
    homeViewModel: HomeViewModel = hiltViewModel(),
    libraryWarmupViewModel: LibraryViewModel = hiltViewModel(),
) {
    val libraries by appViewModel.libraries.collectAsState()
    var splashDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val shownAt = android.os.SystemClock.uptimeMillis()
        withTimeoutOrNull(SPLASH_MAX_MS) { homeViewModel.uiState.first { !it.isLoading } }
        val left = SPLASH_MIN_MS - (android.os.SystemClock.uptimeMillis() - shownAt)
        if (left > 0) delay(left)
        StartupPrefs.await()
        splashDone = true
    }
    // Loads Home for each user and language; HomeScreen only refreshes on return.
    LaunchedEffect(session.userId, com.jellio.tv.data.prefs.DisplayPreferences.displayLanguage) { homeViewModel.load(session) }
    LaunchedEffect(libraries) {
        libraries.firstOrNull()?.let { firstLibrary -> libraryWarmupViewModel.load(session, firstLibrary) }
    }
    // Held until Home has its snapshot or first rows, never shorter than a
    // glance at the logo nor longer than the old fixed hold; past that Home
    // draws its own skeletons.
    if (!splashDone) {
        Box(modifier = Modifier.fillMaxSize().background(JellioBg)) {
            BootSplashMark(modifier = Modifier.fillMaxSize())
        }
    } else {
        JellioTvApp(
            session = session,
            appViewModel = appViewModel,
            deepLinkItemId = deepLinkItemId,
            onDeepLinkConsumed = onDeepLinkConsumed,
            onPlayerActiveChanged = onPlayerActiveChanged,
        )
    }
}

@Composable
private fun JellioTvApp(
    session: Session,
    appViewModel: AppViewModel,
    deepLinkItemId: String?,
    onDeepLinkConsumed: () -> Unit,
    onPlayerActiveChanged: (Boolean) -> Unit = {},
    seasonalEffectsViewModel: SeasonalEffectsViewModel = hiltViewModel(),
    friendsViewModel: FriendsViewModel = hiltViewModel(),
) {
    var routeStack by remember { mutableStateOf(listOf<JellioRoute>(JellioRoute.Home())) }
    // Home keeps its scroll positions and focused card while a title's page is open.
    val screenStates = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val route = routeStack.last()
    LaunchedEffect(route) {
        com.jellio.tv.data.streams.StreamLookupLog.screen(route::class.simpleName ?: "screen")
        onPlayerActiveChanged(route is JellioRoute.Player || route is JellioRoute.LivePlayer)
    }
    var showAccountSwitcher by remember { mutableStateOf(false) }
    val friendsState by friendsViewModel.state.collectAsState()
    LaunchedEffect(session.userId) {
        friendsViewModel.onSession(session)
        while (true) {
            delay(FRIENDS_POLL_MS)
            friendsViewModel.refresh()
        }
    }
    // A profile switch swaps the session under the open switcher: close it.
    LaunchedEffect(session.userId) { showAccountSwitcher = false }
    var showNotifications by remember { mutableStateOf(false) }
    var showNowPlayingDrawer by remember { mutableStateOf(false) }
    var quickHubInitialTab by remember { mutableStateOf(QuickHubTab.NOW_PLAYING) }
    val nowPlayingSessions by appViewModel.nowPlayingManager.sessions.collectAsState()
    val nowPlayingLocalPaused by appViewModel.nowPlayingManager.localPaused.collectAsState()
    var showChangelog by remember { mutableStateOf(false) }
    var showOnboarding by remember { mutableStateOf(false) }
    var initialAudioLang by remember { mutableStateOf<String?>(null) }
    var initialSubtitleLang by remember { mutableStateOf<String?>(null) }
    var changelogReleaseToDisplay by remember { mutableStateOf(com.jellio.tv.ui.changelog.CURRENT_CHANGELOG) }
    var streamPickerItem by remember { mutableStateOf<BaseItemDto?>(null) }
    val libraries by appViewModel.libraries.collectAsState()
    var homeEditMode by remember(route) {
        mutableStateOf((route as? JellioRoute.Home)?.editMode == true)
    }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(session.userId) {
        if (com.jellio.tv.ui.onboarding.OnboardingPrefs.shouldShowOnboarding(context, session.userId)) {
            val config = appViewModel.getUserConfiguration(session.userId)
            initialAudioLang = config?.AudioLanguagePreference?.trim()?.ifBlank { null }
            val subMode = config?.SubtitleMode?.trim()
            val subPref = config?.SubtitleLanguagePreference?.trim()?.ifBlank { null }
            initialSubtitleLang = if (subMode.equals("None", ignoreCase = true) || subPref == "off") "off" else subPref
            showNotifications = false
            showChangelog = false
            showOnboarding = true
        } else if (com.jellio.tv.ui.changelog.ChangelogPrefs.shouldShowChangelog(context)) {
            changelogReleaseToDisplay = com.jellio.tv.ui.changelog.LATEST_BIG_CHANGELOG
            showNotifications = false
            showChangelog = true
        }
    }

    val appLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(appLifecycle) {
        appLifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) { seasonalEffectsViewModel.runWhileStarted() }
    }
    val seasonalTheme by seasonalEffectsViewModel.activeTheme.collectAsState()
    val sidebarFocusRequester = remember { FocusRequester() }
    var sidebarHasRealFocus by remember { mutableStateOf(false) }
    var sidebarForceCollapsed by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // The shortcut only claims its key where the hub can actually open; elsewhere the key works as usual.
    val quickHubShortcutAllowed = !route.isImmersive() && (
        showNowPlayingDrawer || (
            !homeEditMode && streamPickerItem == null &&
                !(showAccountSwitcher || showNotifications || showChangelog || showOnboarding)
            )
        )
    androidx.compose.runtime.DisposableEffect(route, showNowPlayingDrawer, quickHubShortcutAllowed) {
        val act = (context as? MainActivity)
        act?.isQuickHubOpen = showNowPlayingDrawer && !route.isImmersive()
        val toggleHandler = {
            quickHubInitialTab = QuickHubTab.NOW_PLAYING
            showNowPlayingDrawer = !showNowPlayingDrawer
        }
        act?.onQuickHubShortcut = if (quickHubShortcutAllowed) toggleHandler else null
        onDispose {
            act?.isQuickHubOpen = false
            act?.onQuickHubShortcut = null
        }
    }

    fun switchTab(target: JellioRoute) {
        routeStack = listOf(target)
    }

    fun push(target: JellioRoute) {
        routeStack = routeStack + target
    }

    fun openQuickHub(tab: QuickHubTab) {
        quickHubInitialTab = tab
        showNowPlayingDrawer = true
    }

    BackHandler(enabled = routeStack.size > 1) {
        routeStack = routeStack.dropLast(1)
    }

    val onNavigateToDetail: (String) -> Unit = { itemId -> push(JellioRoute.Detail(itemId)) }
    val onNavigateToDetailReplace: (String) -> Unit = { itemId ->
        if (routeStack.lastOrNull() is JellioRoute.Detail) {
            routeStack = routeStack.dropLast(1) + JellioRoute.Detail(itemId)
        } else {
            push(JellioRoute.Detail(itemId))
        }
    }
    val onNavigateToPerson: (String) -> Unit = { personId -> push(JellioRoute.Person(personId)) }
    fun playItem(itemId: String, mediaSourceId: String? = null, startPositionTicks: Long? = null) {
        push(JellioRoute.Player(itemId, mediaSourceId, startPositionTicks))
    }
    val onPlayDirect: (String, String?) -> Unit = { itemId, mediaSourceId -> playItem(itemId, mediaSourceId) }

    LaunchedEffect(appViewModel) {
        appViewModel.remoteControlManager.playCommands.collect { cmd ->
            if (routeStack.lastOrNull() is JellioRoute.Player) {
                routeStack = routeStack.dropLast(1)
            }
            playItem(cmd.itemId, cmd.mediaSourceId, cmd.startPositionTicks)
        }
    }
    // Play the way the title page does: one stream plays, several (with
    // nothing remembered) open the picker, none opens the picker to say so.
    val onPlay: (BaseItemDto) -> Unit = { item ->
        if (item.Type != "Movie" && item.Type != "Episode") onPlayDirect(item.Id, null) else scope.launch {
            when (val action = runCatching { appViewModel.resolvePlayAction(session, item) }.getOrNull()) {
                is PlayAction.Direct -> onPlayDirect(action.itemId, action.mediaSourceId)
                is PlayAction.ShowPicker -> streamPickerItem = action.item
                // The lookup failed: the picker says why and can try again.
                null -> streamPickerItem = item
            }
        }
    }
    val onPlayId: (String, Boolean) -> Unit = { itemId, leavePlayer ->
        scope.launch {
            val item = appViewModel.getItemOrNull(session, itemId)
            val empty = item != null && (item.Type == "Movie" || item.Type == "Episode") && runCatching { appViewModel.getMediaSources(session, item.Id) }.getOrNull()?.isEmpty() == true
            if (leavePlayer) routeStack = routeStack.dropLast(1)
            if (empty) streamPickerItem = item else onPlayDirect(itemId, null)
        }
    }

    LaunchedEffect(deepLinkItemId) {
        val itemId = deepLinkItemId ?: return@LaunchedEffect
        if (itemId.startsWith("detail:")) onNavigateToDetail(itemId.removePrefix("detail:")) else onPlayId(itemId, false)
        onDeepLinkConsumed()
    }

    val memoizedImageUrl = remember(session) {
        { item: com.jellio.tv.data.model.BaseItemDto, imageType: String, maxWidth: Int ->
            appViewModel.imageUrl(session, item, imageType, maxWidth)
        }
    }
    val memoizedRawImageUrl = remember(session) {
        { itemId: String, tag: String?, imageType: String, maxWidth: Int ->
            appViewModel.rawImageUrl(session, itemId, tag, imageType, maxWidth)
        }
    }
    val memoizedServiceLogoUrl = remember(session) {
        { name: String -> appViewModel.serviceLogoUrl(session, name) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!route.isImmersive()) {
            SeasonalEffectsOverlay(themeKey = seasonalTheme, modifier = Modifier.fillMaxSize())
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .let { if (!route.isImmersive()) it.padding(start = SidebarReservedWidth.scaled()) else it },
        ) {
        when (val current = route) {
            is JellioRoute.Profile -> ProfileScreen(
                session = session,
                userId = current.userId,
                userImageUrl = { userId, tag, maxWidth -> appViewModel.userImageUrl(session, userId, tag, maxWidth) },
                bannerUrl = { userId -> appViewModel.bannerUrl(session, userId) },
                itemImageUrl = memoizedRawImageUrl,
                onNavigateToDetail = onNavigateToDetail,
                onLogout = { appViewModel.logout() },
                onOpenProfile = { id -> push(JellioRoute.Profile(id)) },
                modifier = Modifier.fillMaxSize(),
            )
            JellioRoute.Feed -> FeedScreen(
                userId = session.userId,
                imageUrl = memoizedRawImageUrl,
                userImageUrl = { userId, tag, maxWidth -> appViewModel.userImageUrl(session, userId, tag, maxWidth) },
                onUserClick = { userId -> push(JellioRoute.Profile(userId)) },
                modifier = Modifier.fillMaxSize(),
            )
            is JellioRoute.Home -> screenStates.SaveableStateProvider("home") {
                HomeScreen(
                session = session,
                imageUrl = memoizedImageUrl,
                rawImageUrl = memoizedRawImageUrl,
                serviceLogoUrl = memoizedServiceLogoUrl,
                onItemClick = { item -> onNavigateToDetail(item.Id) },
                onComingSoonClick = onNavigateToDetail,
                onServiceClick = { name -> push(JellioRoute.Service(name)) },
                onEditModeChange = { editing ->
                    homeEditMode = editing
                    if (!editing && current.editMode) {
                        routeStack = listOf(JellioRoute.Home(editMode = false))
                    }
                },
                onPlay = onPlay,
                onOpenNowPlaying = {
                    openQuickHub(QuickHubTab.NOW_PLAYING)
                },
                onPlayManually = { item ->
                    scope.launch {
                        when (val action = appViewModel.resolvePlayAction(session, item, forceChoice = true)) {
                            is PlayAction.Direct -> onPlayDirect(action.itemId, action.mediaSourceId)
                            is PlayAction.ShowPicker -> streamPickerItem = action.item
                        }
                    }
                },
                initialEditMode = current.editMode,
                modifier = Modifier.fillMaxSize(),
            )
            }
            JellioRoute.Search -> SearchScreen(
                session = session,
                imageUrl = memoizedImageUrl,
                onItemClick = { item -> onNavigateToDetail(item.Id) },
                modifier = Modifier.fillMaxSize(),
            )
            is JellioRoute.LibraryTab -> {
                val library = libraries.firstOrNull { it.Id == current.libraryId && it.Name.orEmpty() == current.name }
                if (library != null) {
                    androidx.compose.runtime.key(current.libraryId + current.name) {
                        LibraryScreen(
                            session = session,
                            library = library,
                            imageUrl = memoizedImageUrl,
                            onItemClick = { item -> onNavigateToDetail(item.Id) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            JellioRoute.Settings -> SettingsScreen(
                session = session,
                onLogout = { appViewModel.logout() },
                onViewProfile = { push(JellioRoute.Profile()) },
                onCustomizeHome = {
                    routeStack = listOf(JellioRoute.Home(editMode = true))
                },
                onShowChangelog = {
                    changelogReleaseToDisplay = com.jellio.tv.ui.changelog.CURRENT_CHANGELOG
                    showChangelog = true
                },
                onLaunchOnboarding = { showOnboarding = true },
                onOpenQuickHub = {
                    openQuickHub(QuickHubTab.NOW_PLAYING)
                },
                modifier = Modifier.fillMaxSize(),
            )
            is JellioRoute.Detail -> androidx.compose.runtime.key(current.itemId) {
                DetailScreen(
                    session = session,
                    itemId = current.itemId,
                    imageUrl = { itemId, tag, imageType, maxWidth -> appViewModel.rawImageUrl(session, itemId, tag, imageType, maxWidth) },
                    onNavigateToDetail = onNavigateToDetail,
                    onNavigateToDetailReplace = onNavigateToDetailReplace,
                    onNavigateToPerson = onNavigateToPerson,
                    onOpenStreamPicker = { item -> streamPickerItem = item },
                    onPlayDirect = onPlayDirect,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            is JellioRoute.Person -> androidx.compose.runtime.key(current.personId) {
                PersonScreen(
                    session = session,
                    personId = current.personId,
                    imageUrl = { itemId, tag, imageType, maxWidth -> appViewModel.rawImageUrl(session, itemId, tag, imageType, maxWidth) },
                    onBack = { routeStack = routeStack.dropLast(1) },
                    onItemClick = onNavigateToDetail,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            is JellioRoute.Service -> androidx.compose.runtime.key(current.name) {
                ServiceScreen(
                    session = session,
                    serviceName = current.name,
                    logoUrl = appViewModel.serviceLogoUrl(session, current.name),
                    imageUrl = { item, imageType, maxWidth -> appViewModel.imageUrl(session, item, imageType, maxWidth) },
                    onItemClick = { item -> onNavigateToDetail(item.Id) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            is JellioRoute.Player -> PlayerScreen(
                session = session,
                itemId = current.itemId,
                mediaSourceId = current.mediaSourceId,
                startPositionTicks = current.startPositionTicks,
                onBack = { routeStack = routeStack.dropLast(1) },
                onPlayNext = { nextItemId -> onPlayId(nextItemId, true) },
                onPlayPrevious = { previousId ->
                    routeStack = routeStack.dropLast(1)
                    playItem(previousId, null, 0L)
                },
                modifier = Modifier.fillMaxSize(),
            )
            JellioRoute.LiveTv -> com.jellio.tv.ui.live.LiveTvScreen(
                session = session,
                imageUrl = { itemId, tag -> memoizedRawImageUrl(itemId, tag, "Primary", 240) },
                onPlay = { channelId -> push(JellioRoute.LivePlayer(channelId)) },
                modifier = Modifier.fillMaxSize(),
            )
            is JellioRoute.LivePlayer -> androidx.compose.runtime.key(current.channelId) {
                com.jellio.tv.ui.live.LivePlayerScreen(
                    session = session,
                    channelId = current.channelId,
                    imageUrl = { itemId, tag -> memoizedRawImageUrl(itemId, tag, "Primary", 240) },
                    onBack = { routeStack = routeStack.dropLast(1) },
                )
            }
        }
        }

        if (!route.isImmersive()) {
            SeasonalSkyOverlay(themeKey = seasonalTheme, modifier = Modifier.fillMaxSize())
        }

        if (!route.isImmersive()) {
            LaunchedEffect(route) {
                sidebarForceCollapsed = true
                snapshotFlow { sidebarHasRealFocus }.collect { hasFocus ->
                    if (hasFocus && sidebarForceCollapsed) {
                        var moved = focusManager.moveFocus(FocusDirection.Right)
                        var tries = 0
                        while (!moved && tries < 16 && sidebarHasRealFocus) {
                            delay(150)
                            moved = !sidebarHasRealFocus || focusManager.moveFocus(FocusDirection.Right)
                            tries++
                        }
                        if (!moved) sidebarForceCollapsed = false
                    } else if (!hasFocus) {
                        sidebarForceCollapsed = false
                    }
                }
            }
            SidebarNav(
                items = jellioNavItems(libraries),
                selected = route,
                enabled = !homeEditMode,
                focusable = !homeEditMode && !(showAccountSwitcher || showNotifications || showChangelog || showOnboarding || showNowPlayingDrawer),
                onSelect = { clicked ->
                    if (clicked is JellioRoute.Profile && clicked.userId == null) {
                        showAccountSwitcher = true
                    } else {
                        switchTab(clicked)
                    }
                },
                profileAvatarUrl = appViewModel.userImageUrl(session, session.userId, null, 200),
                profileName = session.userName,
                profileBadge = badgeVisible(friendsState.incoming.size),
                restoreFocusRequester = sidebarFocusRequester,
                forceCollapsed = sidebarForceCollapsed,
                onFocusChange = { sidebarHasRealFocus = it },
            )
            if (showAccountSwitcher) {
                AccountSwitcherOverlay(
                    session = session,
                    onDismiss = {
                        sidebarFocusRequester.requestFocus()
                        showAccountSwitcher = false
                    },
                    onViewProfile = { push(JellioRoute.Profile()) },
                    onOpenSettings = { switchTab(JellioRoute.Settings) },
                    onSignOut = { appViewModel.logout() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (showNotifications) {
                com.jellio.tv.ui.notify.NotificationsOverlay(
                    onDismiss = {
                        showNotifications = false
                        runCatching { sidebarFocusRequester.requestFocus() }
                    },
                    onOpenItem = { itemId -> onNavigateToDetail(itemId.replace("-", "")) },
                    onOpenLiveTv = { switchTab(JellioRoute.LiveTv) },
                    onOpenGroupWatch = {
                        showNotifications = false
                        openQuickHub(QuickHubTab.GROUP_WATCH)
                    },
                )
            }
            if (showOnboarding) {
                com.jellio.tv.ui.onboarding.OnboardingOverlay(
                    userId = session.userId,
                    initialAudioLang = initialAudioLang,
                    initialSubtitleLang = initialSubtitleLang,
                    onSavePreferences = { audio, subtitle ->
                        appViewModel.saveLanguagePreferences(session, audio, subtitle)
                    },
                    onComplete = {
                        showOnboarding = false
                        runCatching { sidebarFocusRequester.requestFocus() }
                    },
                )
            }
            if (showChangelog) {
                com.jellio.tv.ui.changelog.ChangelogPopup(
                    release = changelogReleaseToDisplay,
                    onDismiss = {
                        com.jellio.tv.ui.changelog.ChangelogPrefs.markSeen(context, changelogReleaseToDisplay.version)
                        showChangelog = false
                    },
                )
            }
            if (showNowPlayingDrawer) {
                com.jellio.tv.ui.nowplaying.NowPlayingDrawer(
                    session = session,
                    sessions = nowPlayingSessions,
                    localPaused = nowPlayingLocalPaused,
                    initialTab = quickHubInitialTab,
                    onDismiss = {
                        showNowPlayingDrawer = false
                        runCatching { sidebarFocusRequester.requestFocus() }
                    },
                    onHandoff = { sess ->
                        showNowPlayingDrawer = false
                        sess.Item?.Id?.let { itemId ->
                            playItem(itemId, startPositionTicks = sess.PositionTicks)
                        }
                    },
                    onNavigateToDetail = { itemId ->
                        showNowPlayingDrawer = false
                        onNavigateToDetail(itemId)
                    },
                    onTuneIn = { channelId ->
                        showNowPlayingDrawer = false
                        push(JellioRoute.LivePlayer(channelId))
                    },
                    onResumeLocal = {
                        showNowPlayingDrawer = false
                        nowPlayingLocalPaused?.let { paused ->
                            playItem(paused.itemId, startPositionTicks = paused.positionTicks)
                        }
                    },
                    onDismissLocal = {
                        appViewModel.nowPlayingManager.setLocalPaused(null)
                    },
                    onOpenLiveTv = {
                        showNowPlayingDrawer = false
                        switchTab(JellioRoute.LiveTv)
                    },
                    onOpenWatchlist = {
                        showNowPlayingDrawer = false
                        switchTab(JellioRoute.Home())
                    },
                    onOpenProfile = { id ->
                        showNowPlayingDrawer = false
                        push(JellioRoute.Profile(id))
                    },
                    pollNowPlaying = { appViewModel.nowPlayingManager.pollClosely() },
                    rawImageUrl = memoizedRawImageUrl,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        val pickerItem = streamPickerItem
        if (pickerItem != null) {
            StreamPickerOverlay(
                item = pickerItem,
                backdropUrl = pickerItem.BackdropImageTags?.firstOrNull()?.let {
                    appViewModel.rawImageUrl(session, pickerItem.Id, it, "Backdrop", 1920)
                } ?: pickerItem.ImageTags?.get("Primary")?.let {
                    appViewModel.rawImageUrl(session, pickerItem.Id, it, "Primary", 1920)
                },
                loadSources = { forceFresh -> appViewModel.getMediaSources(session, pickerItem.Id, forceFresh) },
                rememberedSourceId = { appViewModel.rememberedMediaSourceId(pickerItem.Id) },
                loadReadyInfo = { appViewModel.readyInfo(pickerItem.Id) },
                onSelect = { source ->
                    streamPickerItem = null
                    val mediaSourceId = source.Id
                    if (mediaSourceId != null) {
                        scope.launch { appViewModel.rememberStreamChoice(pickerItem.Id, mediaSourceId) }
                    }
                    onPlayDirect(pickerItem.Id, mediaSourceId)
                },
                onDismiss = { streamPickerItem = null },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Announcements from the server, over everything (Home, the
        // player, any screen).
        com.jellio.tv.ui.announce.AnnouncementToast(modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter))
        com.jellio.tv.ui.notify.NewNotificationsPill(
            immersive = route.isImmersive(),
            modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter),
        )
        com.jellio.tv.ui.announce.SoundPlayerHost()
        com.jellio.tv.ui.announce.ScreenOverlayHost()
        com.jellio.tv.ui.groupwatch.GroupWatchHost(
            session = session,
            isPlaying = { id ->
                (routeStack.lastOrNull() as? JellioRoute.Player)?.let { com.jellio.tv.data.syncplay.SyncPlayManager.sameId(it.itemId, id) } == true
            },
            // Following the group replaces whatever the player had open.
            openItem = { id ->
                if (routeStack.lastOrNull() is JellioRoute.Player) routeStack = routeStack.dropLast(1)
                onPlayDirect(id, null)
            },
        )
    }
}
