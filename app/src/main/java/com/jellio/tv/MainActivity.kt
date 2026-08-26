package com.jellio.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
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
import com.jellio.tv.ui.home.HomeScreen
import com.jellio.tv.ui.library.LibraryScreen
import com.jellio.tv.ui.nav.JellioNavItems
import com.jellio.tv.ui.nav.JellioRoute
import com.jellio.tv.ui.nav.LibraryPickerOverlay
import com.jellio.tv.ui.nav.TopNavPill
import com.jellio.tv.ui.nav.isImmersive
import com.jellio.tv.ui.nowplaying.NowPlayingButton
import com.jellio.tv.ui.nowplaying.NowPlayingPanel
import com.jellio.tv.ui.nowplaying.NowPlayingViewModel
import com.jellio.tv.ui.person.PersonScreen
import com.jellio.tv.ui.player.PlayerScreen
import com.jellio.tv.ui.profile.ProfileScreen
import com.jellio.tv.ui.search.SearchScreen
import com.jellio.tv.ui.service.ServiceScreen
import com.jellio.tv.ui.settings.SettingsScreen
import com.jellio.tv.ui.theme.JellioTvTheme
import com.jellio.tv.ui.watchlist.WatchlistScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JellioTvTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    JellioTvRoot()
                }
            }
        }
    }
}

@Composable
private fun JellioTvRoot(appViewModel: AppViewModel = hiltViewModel()) {
    when (val state = appViewModel.authState.collectAsState().value) {
        // A DataStore-backed sessionFlow always eventually emits, but
        // there is a real async gap before its first value: an empty
        // Box here for one frame beats a false flash of the sign in
        // screen for a reader who is actually already signed in.
        AuthState.Loading -> Box(Modifier.fillMaxSize()) {}
        AuthState.LoggedOut -> LoginScreen(modifier = Modifier.fillMaxSize())
        is AuthState.LoggedIn -> JellioTvApp(session = state.session, appViewModel = appViewModel)
    }
}

@Composable
private fun JellioTvApp(
    session: Session,
    appViewModel: AppViewModel,
    nowPlayingViewModel: NowPlayingViewModel = hiltViewModel(),
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
    var streamPickerItem by remember { mutableStateOf<BaseItemDto?>(null) }
    val libraries by appViewModel.libraries.collectAsState()
    // Shared with TopNavPill below: the one real D-pad Down landing
    // spot every non-immersive screen's own top scrollable container
    // attaches itself to (only one is ever actually composed at a
    // time), so the pill has somewhere real to send focus instead of
    // trapping it, regardless of which one of them is on real screen.
    val contentFocusRequester = remember { FocusRequester() }
    // Real components/mobileNav.js's own scroll-driven compact state,
    // threaded up from whichever non-immersive screen is actually
    // composed (each reports its own real scroll position via
    // onCompactChange). Reset to expanded on every real tab switch,
    // same real reasoning that file's own header documents: a fresh
    // screen starts scrolled to its own top, so the pill should too.
    var navCompact by remember { mutableStateOf(false) }
    LaunchedEffect(route) { navCompact = false }
    val scope = rememberCoroutineScope()

    // Real components/nowPlaying.js's own startNowPlaying(): begun once
    // a real session is confirmed signed in (JellioTvApp is only ever
    // composed once AuthState.LoggedIn, matching that file's own real
    // "called from app.js's own sync() once authenticated" reasoning),
    // NowPlayingViewModel's own start() guarding against a second real
    // poll loop if this composable recomposes.
    LaunchedEffect(Unit) { nowPlayingViewModel.start() }
    val nowPlayingSessions by nowPlayingViewModel.sessions.collectAsState()
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

    Box(modifier = Modifier.fillMaxSize()) {
        when (val current = route) {
            JellioRoute.Profile -> ProfileScreen(
                session = session,
                onLogout = { appViewModel.logout() },
                modifier = Modifier.fillMaxSize(),
            )
            JellioRoute.Home -> HomeScreen(
                session = session,
                imageUrl = { item, imageType, maxWidth -> appViewModel.imageUrl(session, item, imageType, maxWidth) },
                rawImageUrl = { itemId, tag, imageType, maxWidth -> appViewModel.rawImageUrl(session, itemId, tag, imageType, maxWidth) },
                serviceLogoUrl = { name -> appViewModel.serviceLogoUrl(session, name) },
                contentFocusRequester = contentFocusRequester,
                onItemClick = { item -> onNavigateToDetail(item.Id) },
                onComingSoonClick = onNavigateToDetail,
                onServiceClick = { name -> push(JellioRoute.Service(name)) },
                onCompactChange = { navCompact = it },
                modifier = Modifier.fillMaxSize(),
            )
            JellioRoute.Search -> SearchScreen(
                session = session,
                imageUrl = { item, imageType, maxWidth -> appViewModel.imageUrl(session, item, imageType, maxWidth) },
                onItemClick = { item -> onNavigateToDetail(item.Id) },
                contentFocusRequester = contentFocusRequester,
                onCompactChange = { navCompact = it },
                modifier = Modifier.fillMaxSize(),
            )
            JellioRoute.Watchlist -> WatchlistScreen(
                session = session,
                imageUrl = { item, imageType, maxWidth -> appViewModel.imageUrl(session, item, imageType, maxWidth) },
                onItemClick = { item -> onNavigateToDetail(item.Id) },
                contentFocusRequester = contentFocusRequester,
                onCompactChange = { navCompact = it },
                modifier = Modifier.fillMaxSize(),
            )
            JellioRoute.Calendar -> CalendarScreen(
                imageUrl = { itemId, tag, imageType, maxWidth -> appViewModel.rawImageUrl(session, itemId, tag, imageType, maxWidth) },
                onItemClick = onNavigateToDetail,
                contentFocusRequester = contentFocusRequester,
                onCompactChange = { navCompact = it },
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
                        contentFocusRequester = contentFocusRequester,
                        onCompactChange = { navCompact = it },
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
                }
            }
            JellioRoute.Settings -> SettingsScreen(
                session = session,
                onLogout = { appViewModel.logout() },
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

        if (!route.isImmersive()) {
            TopNavPill(
                items = JellioNavItems,
                selected = route,
                contentFocusRequester = contentFocusRequester,
                isCompact = navCompact,
                onSelect = { clicked ->
                    // Mirrors components/mobileNav.js's own single Library
                    // button: a tap opens the picker rather than
                    // navigating straight there, since no one real
                    // library speaks for the button itself.
                    if (clicked is JellioRoute.Library) {
                        showLibraryPicker = true
                    } else {
                        switchTab(clicked)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            // Real components/nowPlaying.js's own trigger button: this
            // app's own TopNavPill has no sidebar of its own to anchor
            // it to (mirrors mobileNav.js's floating pill instead, see
            // JellioRoute.kt's own header comment), so it sits as its
            // own real corner button alongside the pill instead,
            // reachable from every same non-immersive screen the real
            // sidebar version would have been.
            NowPlayingButton(
                sessionCount = nowPlayingSessions.size,
                onClick = { showNowPlayingPanel = !showNowPlayingPanel },
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 32.dp, end = 32.dp),
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
    }
}
