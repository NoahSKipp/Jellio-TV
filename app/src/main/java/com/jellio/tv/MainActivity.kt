package com.jellio.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Surface
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.AppViewModel
import com.jellio.tv.ui.AuthState
import com.jellio.tv.ui.auth.LoginScreen
import com.jellio.tv.ui.common.PlaceholderScreen
import com.jellio.tv.ui.home.HomeScreen
import com.jellio.tv.ui.nav.JellioNavItems
import com.jellio.tv.ui.nav.JellioRoute
import com.jellio.tv.ui.nav.LibraryPickerOverlay
import com.jellio.tv.ui.nav.TopNavPill
import com.jellio.tv.ui.profile.ProfileScreen
import com.jellio.tv.ui.theme.JellioTvTheme
import dagger.hilt.android.AndroidEntryPoint

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
private fun JellioTvApp(session: Session, appViewModel: AppViewModel) {
    var route by remember { mutableStateOf<JellioRoute>(JellioRoute.Home) }
    var selectedLibrary by remember { mutableStateOf<BaseItemDto?>(null) }
    var showLibraryPicker by remember { mutableStateOf(false) }
    val libraries by appViewModel.libraries.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        when (route) {
            JellioRoute.Profile -> ProfileScreen(
                session = session,
                onLogout = { appViewModel.logout() },
                modifier = Modifier.fillMaxSize(),
            )
            JellioRoute.Home -> HomeScreen(
                session = session,
                imageUrl = { item, imageType, maxWidth -> appViewModel.imageUrl(session, item, imageType, maxWidth) },
                modifier = Modifier.fillMaxSize(),
            )
            JellioRoute.Search -> PlaceholderScreen("Search", modifier = Modifier.fillMaxSize())
            JellioRoute.Watchlist -> PlaceholderScreen("Watchlist", modifier = Modifier.fillMaxSize())
            JellioRoute.Calendar -> PlaceholderScreen("Calendar", modifier = Modifier.fillMaxSize())
            JellioRoute.Library -> PlaceholderScreen(
                selectedLibrary?.Name ?: "Library",
                modifier = Modifier.fillMaxSize(),
            )
            JellioRoute.Settings -> PlaceholderScreen("Settings", modifier = Modifier.fillMaxSize())
        }
        TopNavPill(
            items = JellioNavItems,
            selected = route,
            onSelect = { clicked ->
                // Mirrors components/mobileNav.js's own single Library
                // button: a tap opens the picker rather than
                // navigating straight there, since no one real
                // library speaks for the button itself.
                if (clicked is JellioRoute.Library) {
                    showLibraryPicker = true
                } else {
                    route = clicked
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (showLibraryPicker) {
            LibraryPickerOverlay(
                // Already the real curated nav set (Movies/Shows/Anime,
                // JellioRepository.getLibraryNavEntries()'s own real
                // job), not filtered again here.
                libraries = libraries,
                onSelect = { library ->
                    selectedLibrary = library
                    route = JellioRoute.Library
                    showLibraryPicker = false
                },
                onDismiss = { showLibraryPicker = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
