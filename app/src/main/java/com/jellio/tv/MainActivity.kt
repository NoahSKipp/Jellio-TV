package com.jellio.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.tv.material3.Surface
import com.jellio.tv.ui.home.HomeScreen
import com.jellio.tv.ui.home.PlaceholderScreen
import com.jellio.tv.ui.nav.JellioRoute
import com.jellio.tv.ui.nav.TopNavPill
import com.jellio.tv.ui.theme.JellioTvTheme
import dagger.hilt.android.AndroidEntryPoint

// Five flat top-level tabs behind the pill (no drill-down yet), so
// remembered state switches between them directly rather than through
// a NavHost back stack that would only be doing real work once item
// detail/player routes exist on top of these. Real navigation graph
// lands with those.
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JellioTvTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    JellioTvApp()
                }
            }
        }
    }
}

@Composable
private fun JellioTvApp() {
    var route by remember { mutableStateOf(JellioRoute.Home) }

    Box(modifier = Modifier.fillMaxSize()) {
        when (route) {
            JellioRoute.Home -> HomeScreen(modifier = Modifier.fillMaxSize())
            JellioRoute.Search -> PlaceholderScreen("Search", modifier = Modifier.fillMaxSize())
            JellioRoute.Watchlist -> PlaceholderScreen("Watchlist", modifier = Modifier.fillMaxSize())
            JellioRoute.Calendar -> PlaceholderScreen("Calendar", modifier = Modifier.fillMaxSize())
            JellioRoute.Settings -> PlaceholderScreen("Settings", modifier = Modifier.fillMaxSize())
        }
        TopNavPill(
            selected = route,
            onSelect = { route = it },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
