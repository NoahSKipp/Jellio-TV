package com.jellio.tv.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jellio.tv.ui.theme.JellioTextSecondary

// Shared shell for every route that has no real screen yet (Search,
// Watchlist, Calendar, Settings). One composable, not four
// near-identical files, until each of these actually grows its own
// real content and earns its own file the way HomeScreen already
// has.
@Composable
fun PlaceholderScreen(title: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "$title is on the way.",
            style = MaterialTheme.typography.bodyLarge,
            color = JellioTextSecondary,
        )
    }
}
