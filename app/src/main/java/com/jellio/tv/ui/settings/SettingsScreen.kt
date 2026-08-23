package com.jellio.tv.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jellio.tv.BuildConfig
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary

// A reduced real port of screens/settings.js: this app's own real
// server/account facts, not yet that file's own language preference,
// password change or Quick Connect sections, each a real further
// screen of its own worth building out, not guessed at here.
@Composable
fun SettingsScreen(session: Session, onLogout: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(top = 140.dp, start = 48.dp, end = 48.dp)) {
        Text(text = "Settings", style = MaterialTheme.typography.titleLarge)

        SettingsSection(title = "Server") {
            SettingsRow(label = "Address", value = session.serverAddress)
            SettingsRow(label = "Signed in as", value = session.userName)
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
