package com.jellio.tv.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jellio.tv.BuildConfig
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary

// A reduced real port of screens/settings.js: this app's own real
// server/account facts and the same real remember-stream-choice
// preference components/streamPicker.js's own picker reads, not yet
// that file's own language preference, password change or Quick
// Connect sections, each a real further screen of its own worth
// building out, not guessed at here.
@Composable
fun SettingsScreen(
    session: Session,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val rememberStream by viewModel.rememberStream.collectAsState()

    Column(modifier = modifier.fillMaxSize().padding(top = 140.dp, start = 48.dp, end = 48.dp)) {
        Text(text = "Settings", style = MaterialTheme.typography.titleLarge)

        SettingsSection(title = "Server") {
            SettingsRow(label = "Address", value = session.serverAddress)
            SettingsRow(label = "Signed in as", value = session.userName)
        }

        SettingsSection(title = "Playback") {
            SettingsToggleRow(
                label = "Remember stream choice",
                description = "Skip the stream picker on a title you already picked one for, for 4 days.",
                checked = rememberStream,
                onToggle = { viewModel.setRememberStream(!rememberStream) },
            )
        }

        SettingsSection(title = "About") {
            SettingsRow(label = "Version", value = BuildConfig.VERSION_NAME)
        }

        Surface(
            onClick = onLogout,
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = JellioSecondary, contentColor = JellioBg),
            modifier = Modifier.padding(top = 32.dp),
        ) {
            Text(text = "Log Out", modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp))
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
