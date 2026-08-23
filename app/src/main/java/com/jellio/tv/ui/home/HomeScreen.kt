package com.jellio.tv.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jellio.tv.ui.theme.JellioTextSecondary

// First real screen this scaffold ships with: proves the theme, the
// top pill nav and TV focus navigation all work end to end before
// screens/home.js's own real rows (Continue Watching, Up Next,
// library rails, Coming Soon) get ported here against a real Jellio
// API client, not placeholder content pretending to be them.
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Jellio TV", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Rows are on the way.",
                style = MaterialTheme.typography.bodyLarge,
                color = JellioTextSecondary,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
