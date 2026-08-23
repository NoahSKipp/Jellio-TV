package com.jellio.tv.ui.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jellio.tv.ui.common.JellioTextField
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioTextSecondary
import com.jellio.tv.ui.theme.JellioTrending

// Mirrors screens/login.js's own real login: server address, then
// real Jellyfin credentials, real JellioRepository.connectAndLogin()
// underneath rather than anything simulated. Ships before any row
// content because nothing else here can be real without it.
@Composable
fun LoginScreen(modifier: Modifier = Modifier, viewModel: LoginViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var serverAddress by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.width(420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "Jellio TV", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Sign in to your Jellio server",
                style = MaterialTheme.typography.bodyLarge,
                color = JellioTextSecondary,
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
            )
            JellioTextField(
                value = serverAddress,
                onValueChange = { serverAddress = it },
                label = "Server address (https://...)",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(16.dp))
            JellioTextField(
                value = username,
                onValueChange = { username = it },
                label = "Username",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(16.dp))
            JellioTextField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                isPassword = true,
                modifier = Modifier.fillMaxWidth(),
            )
            uiState.error?.let { error ->
                Text(
                    text = error,
                    color = JellioTrending,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Surface(
                onClick = { viewModel.login(serverAddress, username, password) },
                enabled = !uiState.isLoading,
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                colors = ClickableSurfaceDefaults.colors(containerColor = JellioSecondary, contentColor = JellioBg),
            ) {
                Text(
                    text = if (uiState.isLoading) "Signing in..." else "Sign In",
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp),
                )
            }
        }
    }
}
