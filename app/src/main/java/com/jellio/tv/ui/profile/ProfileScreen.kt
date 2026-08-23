package com.jellio.tv.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioTextSecondary

// Mirrors components/sidebar.js's own Profile button/popover, at
// full-screen scale rather than a popover: real signed-in name and
// server, and the one real action Jellio-TV genuinely needs before
// anything else here (Log Out), same reasoning ui/auth/LoginScreen.kt's
// own header gives for shipping login before any row content.
@Composable
fun ProfileScreen(session: Session, onLogout: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = session.userName, style = MaterialTheme.typography.titleLarge)
            Text(
                text = session.serverAddress,
                style = MaterialTheme.typography.bodyLarge,
                color = JellioTextSecondary,
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
            )
            Surface(
                onClick = onLogout,
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                colors = ClickableSurfaceDefaults.colors(containerColor = JellioSecondary, contentColor = JellioBg),
            ) {
                Text(text = "Log Out", modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp))
            }
        }
    }
}
