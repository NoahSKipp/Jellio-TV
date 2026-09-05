package com.jellio.tv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Surface
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.AppViewModel
import com.jellio.tv.ui.AuthState
import com.jellio.tv.ui.PlayAction
import com.jellio.tv.ui.auth.LoginScreen
import com.jellio.tv.ui.calendar.CalendarScreen
import com.jellio.tv.ui.detail.DetailScreen
import com.jellio.tv.ui.detail.StreamPickerOverlay
import com.jellio.tv.ui.feed.FeedScreen
import com.jellio.tv.ui.home.HomeScreen
import com.jellio.tv.ui.home.HomeViewModel
import com.jellio.tv.ui.library.LibraryScreen
import com.jellio.tv.ui.library.LibraryViewModel
import com.jellio.tv.ui.nav.AccountSwitcherOverlay
import com.jellio.tv.ui.nav.JellioNavItems
import com.jellio.tv.ui.nav.JellioRoute
import com.jellio.tv.ui.nav.LibraryPickerOverlay
import com.jellio.tv.ui.nav.SidebarNav
import com.jellio.tv.ui.nav.SidebarReservedWidth
import com.jellio.tv.ui.nav.isImmersive
import com.jellio.tv.ui.nowplaying.NowPlayingPanel
import com.jellio.tv.ui.nowplaying.NowPlayingViewModel
import com.jellio.tv.ui.person.PersonScreen
import com.jellio.tv.ui.player.PlayerScreen
import com.jellio.tv.ui.profile.ProfileScreen
import com.jellio.tv.ui.search.SearchScreen
import com.jellio.tv.ui.seasonal.SeasonalEffectsOverlay
import com.jellio.tv.ui.seasonal.SeasonalEffectsViewModel
import com.jellio.tv.ui.service.ServiceScreen
import com.jellio.tv.ui.settings.SettingsScreen
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioTvTheme
import com.jellio.tv.ui.theme.scaled
import com.jellio.tv.ui.update.AppUpdateViewModel
import com.jellio.tv.ui.update.BootSplashMark
import com.jellio.tv.ui.update.UpdateToast
import com.jellio.tv.ui.watchlist.WatchlistScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // WatchNextSyncer's own real deep link: a Watch Next row tile on
    // Google TV's home launches this Activity through the manifest's
    // own second intent-filter, singleTop keeping it to this same real
    // instance (onNewIntent below) rather than a fresh one stacking
    // underneath whatever screen was already open.
    private val deepLinkItemId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            JellioTvTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    JellioTvRoot(
                        deepLinkItemId = deepLinkItemId.value,
                        onDeepLinkConsumed = { deepLinkItemId.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.host == "jellio.tv" && uri.path == "/play") {
            uri.getQueryParameter("id")?.let { deepLinkItemId.value = it }
        }
    }
}

@Composable
private fun JellioTvRoot(
    deepLinkItemId: String?,
    onDeepLinkConsumed: () -> Unit,
    appViewModel: AppViewModel = hiltViewModel(),
) {
    when (val state = appViewModel.authState.collectAsState().value) {
        // A DataStore-backed sessionFlow always eventually emits, but
        // there is a real async gap before its first value: an empty
        // Box here for one frame beats a false flash of the sign in
        // screen for a reader who is actually already signed in.
        AuthState.Loading -> Box(Modifier.fillMaxSize()) {}
        AuthState.LoggedOut -> LoginScreen(modifier = Modifier.fillMaxSize())
        is AuthState.LoggedIn -> AppBootGate(
            session = state.session,
            appViewModel = appViewModel,
            deepLinkItemId = deepLinkItemId,
            onDeepLinkConsumed = onDeepLinkConsumed,
        )
    }
}

// Real feedback live: "Loading takes forever" on Home and every first
// real Library visit, reported the same way a mobile app's own splash
// screen is what actually covers that wait rather than a reader
// staring at an empty, already-mounted screen while its own fetch
// runs. HomeScreen's own hiltViewModel() call below resolves to this
// exact same instance (both sit under the same real Activity-scoped
// ViewModelStoreOwner, no Navigation Compose backstack entry between
// them to change that), so kicking its real load() off here and
// gating JellioTvApp's own reveal on it finishing means Home is
// already populated the moment it first appears, not loading again in
// front of the reader.
@Composable
private fun AppBootGate(
    session: Session,
    appViewModel: AppViewModel,
    deepLinkItemId: String?,
    onDeepLinkConsumed: () -> Unit,
    homeViewModel: HomeViewModel = hiltViewModel(),
    libraryWarmupViewModel: LibraryViewModel = hiltViewModel(),
    appUpdateViewModel: AppUpdateViewModel = hiltViewModel(),
) {
    val homeState by homeViewModel.uiState.collectAsState()
    val libraries by appViewModel.libraries.collectAsState()
    // 10s: the ~8s this prefetch usually takes plus the 2s of real
    // room asked for, so the splash stays up for at least that long
    // even on a fast/cached load rather than flashing past the mark's
    // own pop-in reveal the moment homeState.isLoading flips.
    var minSplashTimeElapsed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(10_000)
        minSplashTimeElapsed = true
    }
    LaunchedEffect(session.userId) { homeViewModel.load(session) }
    // Checked here rather than inside JellioTvApp: this fires exactly
    // once per real app open the same way this whole gate does, not
    // once per LaunchedEffect(session.userId) key change were it any
    // deeper in a tree that recomposes across tab switches.
    LaunchedEffect(session.userId) { appUpdateViewModel.checkForUpdate() }
    // Real speculative value, not a real parity port: which library a
    // reader opens first is real screen state no server or web source
    // predicts ahead of the real tap, this just warms the same real
    // first entry LibraryPickerOverlay lists first, on the same real
    // Activity-scoped instance LibraryScreen's own hiltViewModel()
    // call later resolves to. A miss (a different library gets opened
    // first) costs nothing beyond this app's own real pre-fix load
    // time; a hit skips it entirely.
    LaunchedEffect(libraries) {
        libraries.firstOrNull()?.let { firstLibrary -> libraryWarmupViewModel.load(session, firstLibrary) }
    }
    if (homeState.isLoading || !minSplashTimeElapsed) {
        Box(modifier = Modifier.fillMaxSize().background(JellioBg)) {
            BootSplashMark(modifier = Modifier.fillMaxSize())
        }
    } else {
        JellioTvApp(
            session = session,
            appViewModel = appViewModel,
            deepLinkItemId = deepLinkItemId,
            onDeepLinkConsumed = onDeepLinkConsumed,
        )
    }
}

@Composable
private fun JellioTvApp(
    session: Session,
    appViewModel: AppViewModel,
    deepLinkItemId: String?,
    onDeepLinkConsumed: () -> Unit,
    nowPlayingViewModel: NowPlayingViewModel = hiltViewModel(),
    seasonalEffectsViewModel: SeasonalEffectsViewModel = hiltViewModel(),
    // AppBootGate's own hiltViewModel() call already kicked off
    // checkForUpdate(); this call resolves to that exact same
    // Activity-scoped instance, just to render whatever it found.
    appUpdateViewModel: AppUpdateViewModel = hiltViewModel(),
) {
    // A plain real back stack rather than Navigation Compose: the
    // fixed tab set below resets it (real Nuvio/mobile-nav behaviour,
    // switching tabs does not stack), Detail/Player push onto it
    // (screens/detail.js's own #/item and #/play routes, real browser
    // history underneath the web build's own back button).
    var routeStack by remember { mutableStateOf(listOf<JellioRoute>(JellioRoute.Home)) }
    val route = routeStack.last()
    var selectedLibrary by remember { mutableStateOf<BaseItemDto?>(null) }
    var showLibraryPicker by remember { mutableStateOf(false) }
    var showAccountSwitcher by remember { mutableStateOf(false) }
    var streamPickerItem by remember { mutableStateOf<BaseItemDto?>(null) }
    val libraries by appViewModel.libraries.collectAsState()
    // Real feedback live: SidebarNav's own items (and Now Playing's own
    // row inside it) stayed reachable while HomeScreen's own Customize
    // mode was active, so a reader mid-reorder could jump straight off
    // with nothing standing in the way. Threaded down the same real
    // way SidebarNav's own enabled is.
    var homeEditMode by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Real components/nowPlaying.js's own startNowPlaying(): begun once
    // a real session is confirmed signed in (JellioTvApp is only ever
    // composed once AuthState.LoggedIn, matching that file's own real
    // "called from app.js's own sync() once authenticated" reasoning),
    // NowPlayingViewModel's own start() guarding against a second real
    // poll loop if this composable recomposes.
    LaunchedEffect(Unit) { nowPlayingViewModel.start() }
    val nowPlayingSessions by nowPlayingViewModel.sessions.collectAsState()
    LaunchedEffect(Unit) { seasonalEffectsViewModel.start() }
    val seasonalTheme by seasonalEffectsViewModel.activeTheme.collectAsState()
    var showNowPlayingPanel by remember { mutableStateOf(false) }

    fun switchTab(target: JellioRoute) {
        routeStack = listOf(target)
    }

    fun push(target: JellioRoute) {
        routeStack = routeStack + target
    }

    BackHandler(enabled = routeStack.size > 1) {
        routeStack = routeStack.dropLast(1)
    }

    val onNavigateToDetail: (String) -> Unit = { itemId -> push(JellioRoute.Detail(itemId)) }
    val onNavigateToPerson: (String) -> Unit = { personId -> push(JellioRoute.Person(personId)) }
    val onPlayDirect: (String, String?) -> Unit = { itemId, mediaSourceId -> push(JellioRoute.Player(itemId, mediaSourceId)) }

    // WatchNextSyncer's own real deep link, consumed the moment this
    // real signed in tree is up and able to push a route at all: a
    // Watch Next row tile tapped from Google TV's own home jumps
    // straight into that title's own player rather than landing on
    // Home first the way a cold app open otherwise would.
    LaunchedEffect(deepLinkItemId) {
        val itemId = deepLinkItemId ?: return@LaunchedEffect
        onPlayDirect(itemId, null)
        onDeepLinkConsumed()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Real components/seasonalEffects.js's own real z-index: 5,
        // pointer-events: none layer, mounted first (bottommost) so
        // every real screen still draws over it, hidden during this
        // route's own real fullscreen player the same real way that
        // file's own .jellio-root-fullscreen rule already does for the
        // sidebar/mobile nav mounts.
        if (!route.isImmersive()) {
            SeasonalEffectsOverlay(themeKey = seasonalTheme, modifier = Modifier.fillMaxSize())
        }
        // SidebarNav sits fixed at x = 0 and always reserves this much
        // real space, collapsed or not (its own header explains why);
        // every non-immersive screen's own content starts clear of it
        // here rather than each one padding around it individually, the
        // same real reason MainActivity itself is the one root
        // threading it down for the corner buttons below.
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
                itemImageUrl = { itemId, tag, imageType, maxWidth -> appViewModel.rawImageUrl(session, itemId, tag, imageType, maxWidth) },
                onNavigateToDetail = onNavigateToDetail,
                onLogout = { appViewModel.logout() },
                modifier = Modifier.fillMaxSize(),
            )
            JellioRoute.Feed -> FeedScreen(
                imageUrl = { itemId, tag, imageType, maxWidth -> appViewModel.rawImageUrl(session, itemId, tag, imageType, maxWidth) },
                userImageUrl = { userId, tag, maxWidth -> appViewModel.userImageUrl(session, userId, tag, maxWidth) },
                onUserClick = { userId -> push(JellioRoute.Profile(userId)) },
                modifier = Modifier.fillMaxSize(),
            )
            JellioRoute.Home -> HomeScreen(
                session = session,
                imageUrl = { item, imageType, maxWidth -> appViewModel.imageUrl(session, item, imageType, maxWidth) },
                rawImageUrl = { itemId, tag, imageType, maxWidth -> appViewModel.rawImageUrl(session, itemId, tag, imageType, maxWidth) },
                serviceLogoUrl = { name -> appViewModel.serviceLogoUrl(session, name) },
                onItemClick = { item -> onNavigateToDetail(item.Id) },
                onComingSoonClick = onNavigateToDetail,
                onServiceClick = { name -> push(JellioRoute.Service(name)) },
                onEditModeChange = { homeEditMode = it },
                onPlayDirect = onPlayDirect,
                // Real port of components/cardOptionsMenu.js's own
                // "Play manually" (openStreamPicker(item,
                // {forceChoice: true})): the exact same real
                // AppViewModel.resolvePlayAction/StreamPickerOverlay
                // path DetailScreen's own Change Stream button
                // already reaches below, from a card's own options
                // menu instead of the detail page.
                onPlayManually = { item ->
                    scope.launch {
                        when (val action = appViewModel.resolvePlayAction(session, item, forceChoice = true)) {
                            is PlayAction.Direct -> onPlayDirect(action.itemId, action.mediaSourceId)
                            is PlayAction.ShowPicker -> streamPickerItem = action.item
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            JellioRoute.Search -> SearchScreen(
                session = session,
                imageUrl = { item, imageType, maxWidth -> appViewModel.imageUrl(session, item, imageType, maxWidth) },
                onItemClick = { item -> onNavigateToDetail(item.Id) },
                modifier = Modifier.fillMaxSize(),
            )
            JellioRoute.Watchlist -> WatchlistScreen(
                session = session,
                imageUrl = { item, imageType, maxWidth -> appViewModel.imageUrl(session, item, imageType, maxWidth) },
                onItemClick = { item -> onNavigateToDetail(item.Id) },
                modifier = Modifier.fillMaxSize(),
            )
            JellioRoute.Calendar -> CalendarScreen(
                imageUrl = { itemId, tag, imageType, maxWidth -> appViewModel.rawImageUrl(session, itemId, tag, imageType, maxWidth) },
                onItemClick = onNavigateToDetail,
                modifier = Modifier.fillMaxSize(),
            )
            JellioRoute.Library -> {
                val library = selectedLibrary
                if (library != null) {
                    LibraryScreen(
                        session = session,
                        library = library,
                        imageUrl = { item, imageType, maxWidth -> appViewModel.imageUrl(session, item, imageType, maxWidth) },
                        onItemClick = { item -> onNavigateToDetail(item.Id) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            JellioRoute.Settings -> SettingsScreen(
                session = session,
                onLogout = { appViewModel.logout() },
                onViewProfile = { push(JellioRoute.Profile()) },
                modifier = Modifier.fillMaxSize(),
            )
            is JellioRoute.Detail -> DetailScreen(
                session = session,
                itemId = current.itemId,
                imageUrl = { itemId, tag, imageType, maxWidth -> appViewModel.rawImageUrl(session, itemId, tag, imageType, maxWidth) },
                onBack = { routeStack = routeStack.dropLast(1) },
                onNavigateToDetail = onNavigateToDetail,
                onNavigateToPerson = onNavigateToPerson,
                onOpenStreamPicker = { item -> streamPickerItem = item },
                onPlayDirect = onPlayDirect,
                modifier = Modifier.fillMaxSize(),
            )
            is JellioRoute.Person -> PersonScreen(
                session = session,
                personId = current.personId,
                imageUrl = { itemId, tag, imageType, maxWidth -> appViewModel.rawImageUrl(session, itemId, tag, imageType, maxWidth) },
                onBack = { routeStack = routeStack.dropLast(1) },
                onItemClick = onNavigateToDetail,
                modifier = Modifier.fillMaxSize(),
            )
            is JellioRoute.Service -> ServiceScreen(
                session = session,
                serviceName = current.name,
                imageUrl = { item, imageType, maxWidth -> appViewModel.imageUrl(session, item, imageType, maxWidth) },
                onItemClick = { item -> onNavigateToDetail(item.Id) },
                modifier = Modifier.fillMaxSize(),
            )
            is JellioRoute.Player -> PlayerScreen(
                session = session,
                itemId = current.itemId,
                mediaSourceId = current.mediaSourceId,
                onBack = { routeStack = routeStack.dropLast(1) },
                onPlayNext = { nextItemId -> onPlayDirect(nextItemId, null) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        }

        if (!route.isImmersive()) {
            SidebarNav(
                items = JellioNavItems,
                selected = route,
                enabled = !homeEditMode,
                onSelect = { clicked ->
                    // Mirrors components/mobileNav.js's own single Library
                    // button: a tap opens the picker rather than
                    // navigating straight there, since no one real
                    // library speaks for the button itself.
                    if (clicked is JellioRoute.Library) {
                        showLibraryPicker = true
                    } else if (clicked is JellioRoute.Profile && clicked.userId == null) {
                        // Real components/accountSwitcher.js's own real
                        // Profile button: opens the quick switcher
                        // overlay rather than navigating straight to the
                        // full ProfileScreen, same real reason that
                        // file's own header gives (Settings already
                        // reaches the same real screen on its own).
                        showAccountSwitcher = true
                    } else {
                        switchTab(clicked)
                    }
                },
                // Real components/nowPlaying.js's own trigger: absorbed
                // into this rail's own bottom row instead of its own
                // floating corner spot, see SidebarNav's own header for
                // why.
                nowPlayingSessionCount = nowPlayingSessions.size,
                onNowPlayingClick = { showNowPlayingPanel = !showNowPlayingPanel },
                profileAvatarUrl = appViewModel.userImageUrl(session, session.userId, null, 200),
                profileName = session.userName,
            )
            if (showNowPlayingPanel) {
                NowPlayingPanel(
                    sessions = nowPlayingSessions,
                    imageUrl = { itemId, tag, imageType, maxWidth -> appViewModel.rawImageUrl(session, itemId, tag, imageType, maxWidth) },
                    onDismiss = { showNowPlayingPanel = false },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (showLibraryPicker) {
                LibraryPickerOverlay(
                    // Already the real curated nav set (Movies/Shows/Anime,
                    // JellioRepository.getLibraryNavEntries()'s own real
                    // job), not filtered again here.
                    libraries = libraries,
                    onSelect = { library ->
                        selectedLibrary = library
                        switchTab(JellioRoute.Library)
                        showLibraryPicker = false
                    },
                    onDismiss = { showLibraryPicker = false },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (showAccountSwitcher) {
                AccountSwitcherOverlay(
                    session = session,
                    onDismiss = { showAccountSwitcher = false },
                    onViewProfile = { push(JellioRoute.Profile()) },
                    onOpenSettings = { switchTab(JellioRoute.Settings) },
                    onSignOut = { appViewModel.logout() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        val pickerItem = streamPickerItem
        if (pickerItem != null) {
            StreamPickerOverlay(
                item = pickerItem,
                // streamPicker.js's own buildOverlayShell(): falls back
                // to the item's own Primary poster when there is no
                // real Backdrop tag, rather than leaving the picker
                // background blank for a backdrop-less item.
                backdropUrl = pickerItem.BackdropImageTags?.firstOrNull()?.let {
                    appViewModel.rawImageUrl(session, pickerItem.Id, it, "Backdrop", 1920)
                } ?: pickerItem.ImageTags?.get("Primary")?.let {
                    appViewModel.rawImageUrl(session, pickerItem.Id, it, "Primary", 1920)
                },
                loadSources = { appViewModel.getMediaSources(session, pickerItem.Id) },
                rememberedSourceId = { appViewModel.rememberedMediaSourceId(pickerItem.Id) },
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

        val updateState by appUpdateViewModel.uiState.collectAsState()
        updateState.availableVersion?.let { version ->
            UpdateToast(
                version = version,
                downloading = updateState.downloading,
                onDownload = { appUpdateViewModel.download() },
                onDismiss = { appUpdateViewModel.dismiss() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
