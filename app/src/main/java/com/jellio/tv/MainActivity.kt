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
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.AppViewModel
import com.jellio.tv.ui.AuthState
import com.jellio.tv.ui.auth.LoginScreen
import com.jellio.tv.ui.common.PlaceholderScreen
import com.jellio.tv.ui.home.HomeScreen
import com.jellio.tv.ui.nav.JellioRoute
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
    val libraries by appViewModel.libraries.collectAsState()

    val navItems = remember(libraries) {
        buildList {
            add(JellioRoute.Profile)
            add(JellioRoute.Home)
            add(JellioRoute.Search)
            add(JellioRoute.Watchlist)
            add(JellioRoute.Calendar)
            libraries
                .filter { it.CollectionType == "movies" || it.CollectionType == "tvshows" }
                .forEach { library ->
                    add(JellioRoute.Library(library.Id, library.Name ?: "Library", library.CollectionType))
                }
            add(JellioRoute.Settings)
        }
    }

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
                modifier = Modifier.fillMaxSize(),
            )
            JellioRoute.Search -> PlaceholderScreen("Search", modifier = Modifier.fillMaxSize())
            JellioRoute.Watchlist -> PlaceholderScreen("Watchlist", modifier = Modifier.fillMaxSize())
            JellioRoute.Calendar -> PlaceholderScreen("Calendar", modifier = Modifier.fillMaxSize())
            is JellioRoute.Library -> PlaceholderScreen(current.name, modifier = Modifier.fillMaxSize())
            JellioRoute.Settings -> PlaceholderScreen("Settings", modifier = Modifier.fillMaxSize())
        }
        TopNavPill(
            items = navItems,
            selected = route,
            onSelect = { route = it },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
