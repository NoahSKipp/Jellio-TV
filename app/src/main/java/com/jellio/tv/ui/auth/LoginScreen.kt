package com.jellio.tv.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.jellio.tv.data.model.UserDto
import com.jellio.tv.ui.common.JellioTextField
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioDanger
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary

// Real port of native jellyfin-web's own #/login flow, LoginViewModel.kt's
// own header comment covers why this app themes rather than replaces
// it: server address first on a true first run (SERVER_ENTRY), then
// straight onto "Who's watching?" (PROFILE_PICKER) once that server
// answers back with at least one real remembered or public profile,
// a bare username/password form (MANUAL) only ever the real fallback.
// LoginViewModel.kt's own header comment covers the real state
// machine driving which composable below is on screen.
@Composable
fun LoginScreen(modifier: Modifier = Modifier, viewModel: LoginViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.start() }

    // Real feedback live: every one of this flow's own real steps drew
    // its own visual "Back"/"Cancel" button, real duplicate real
    // affordance for what the remote's own real system Back already
    // does everywhere else in this app. Wired here instead (this
    // screen's own only real BackHandler, nothing here had one before),
    // each step's own real button removed below in favor of it.
    BackHandler(enabled = uiState.mode == LoginMode.MANUAL && uiState.canReturnToProfiles) {
        viewModel.backToProfiles()
    }
    BackHandler(enabled = uiState.mode == LoginMode.FORGOT_USERNAME || uiState.mode == LoginMode.FORGOT_PIN) {
        viewModel.cancelForgotPassword()
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (uiState.mode) {
            // Left blank rather than flashing the manual form for the
            // one frame before the real server/profile check resolves.
            LoginMode.CHECKING -> Unit
            LoginMode.SERVER_ENTRY -> ServerAddressForm(
                uiState = uiState,
                onContinue = { server -> viewModel.submitServerAddress(server) },
            )
            LoginMode.PROFILE_PICKER -> ProfilePicker(
                uiState = uiState,
                onQuickSignIn = { viewModel.quickSignIn(it) },
                onForget = { viewModel.forgetRememberedUser(it) },
                onSelectPublicUser = { viewModel.selectPublicUser(it) },
                onAddOther = { viewModel.showManualForm() },
                avatarUrl = { userId, tag -> viewModel.avatarUrl(userId, tag) },
            )
            LoginMode.MANUAL -> ManualLoginForm(
                uiState = uiState,
                onLogin = { server, username, password -> viewModel.login(server, username, password) },
                onForgotPassword = { username -> viewModel.showForgotPassword(username) },
            )
            LoginMode.FORGOT_USERNAME -> ForgotUsernameForm(
                uiState = uiState,
                onSubmit = { username -> viewModel.requestPasswordReset(username) },
            )
            LoginMode.FORGOT_PIN -> ForgotPinForm(
                uiState = uiState,
                onSubmit = { pin, newPassword, confirmPassword -> viewModel.redeemPasswordReset(pin, newPassword, confirmPassword) },
            )
        }
    }
}

// Real port of native jellyfin-web's own #/login server-address step,
// LoginViewModel.kt's own header comment covers why this app themes
// rather than replaces it: split out from ManualLoginForm below so a
// true first run only ever asks for the one thing it actually needs
// before it can even know whether a profile picker is possible,
// rather than username/password fields sitting there with no server
// yet to check them against.
@Composable
private fun ServerAddressForm(
    uiState: LoginUiState,
    onContinue: (String) -> Unit,
) {
    var serverAddress by remember(uiState.serverAddress) { mutableStateOf(uiState.serverAddress) }

    Column(
        modifier = Modifier.width(420.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Jellio TV", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "Enter your Jellio server address to get started",
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
        uiState.error?.let { error ->
            Text(text = error, color = JellioDanger, modifier = Modifier.padding(top = 16.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Surface(
            onClick = { onContinue(serverAddress) },
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = JellioSecondary, contentColor = JellioBg),
        ) {
            Text(text = "Continue", modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp))
        }
    }
}

@Composable
private fun ProfilePicker(
    uiState: LoginUiState,
    onQuickSignIn: (String) -> Unit,
    onForget: (String) -> Unit,
    onSelectPublicUser: (UserDto) -> Unit,
    onAddOther: () -> Unit,
    avatarUrl: (String, String?) -> String,
) {
    Column(
        modifier = Modifier.width(760.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Who’s watching?", style = MaterialTheme.typography.titleLarge)
        uiState.error?.let { error ->
            Text(text = error, color = JellioDanger, modifier = Modifier.padding(top = 12.dp))
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(top = 28.dp),
            modifier = Modifier.fillMaxWidth().height(360.dp),
        ) {
            items(uiState.remembered, key = { "r:" + it.userId }) { profile ->
                ProfileTile(
                    name = profile.name,
                    imageUrl = avatarUrl(profile.userId, profile.primaryImageTag),
                    busy = uiState.busyUserId == profile.userId,
                    onClick = { onQuickSignIn(profile.userId) },
                    onRemove = { onForget(profile.userId) },
                )
            }
            items(uiState.publicOnly, key = { "p:" + it.Id }) { user ->
                ProfileTile(
                    name = user.Name,
                    imageUrl = avatarUrl(user.Id, user.PrimaryImageTag),
                    busy = uiState.busyUserId == user.Id,
                    onClick = { onSelectPublicUser(user) },
                    onRemove = null,
                )
            }
            item(key = "add") {
                ProfileTile(name = "Other user", imageUrl = null, busy = false, onClick = onAddOther, onRemove = null, isAddTile = true)
            }
        }
    }
}

@Composable
private fun ProfileTile(
    name: String,
    imageUrl: String?,
    busy: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?,
    isAddTile: Boolean = false,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Surface(
                onClick = onClick,
                enabled = !busy,
                shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                colors = ClickableSurfaceDefaults.colors(containerColor = JellioBgElevated, contentColor = JellioText, focusedContainerColor = Color.White.copy(alpha = 0.18f), focusedContentColor = JellioText),
                modifier = Modifier.size(112.dp),
            ) {
                if (isAddTile) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = null, tint = JellioText)
                    }
                } else if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().aspectRatio(1f),
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Filled.Person, contentDescription = null, tint = JellioText)
                    }
                }
            }
            if (onRemove != null) {
                Surface(
                    onClick = onRemove,
                    shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color.Black.copy(alpha = 0.6f), contentColor = JellioText),
                    modifier = Modifier.align(Alignment.TopEnd).size(28.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Forget this profile", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        Text(
            text = name,
            color = JellioText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp).width(112.dp),
        )
    }
}

@Composable
private fun ManualLoginForm(
    uiState: LoginUiState,
    onLogin: (String, String, String) -> Unit,
    onForgotPassword: (String) -> Unit,
) {
    var serverAddress by remember(uiState.serverAddress) { mutableStateOf(uiState.serverAddress) }
    var username by remember(uiState.manualPrefillUsername) { mutableStateOf(uiState.manualPrefillUsername) }
    var password by remember { mutableStateOf("") }

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
                color = JellioDanger,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Surface(
            onClick = { onLogin(serverAddress, username, password) },
            enabled = !uiState.isLoading,
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = JellioSecondary, contentColor = JellioBg),
        ) {
            Text(
                text = if (uiState.isLoading) "Signing in..." else "Sign In",
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp),
            )
        }
        Surface(
            onClick = { onForgotPassword(username) },
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, contentColor = JellioTextSecondary),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Text(text = "Forgot password?", modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp))
        }
    }
}

// Real port of screens/login.js's own buildForgotUsernameForm(): step
// 1 of 2, POSTed to /Users/ForgotPassword. real Jellyfin gives back
// the exact same generic response whether or not that username
// exists at all (JellioRepository.kt's own requestPasswordReset()
// header comment covers why), so there is nothing more specific this
// screen could show even on a real failure to reach the server.
@Composable
private fun ForgotUsernameForm(
    uiState: LoginUiState,
    onSubmit: (String) -> Unit,
) {
    var username by remember(uiState.forgotPasswordUsername) { mutableStateOf(uiState.forgotPasswordUsername) }

    Column(
        modifier = Modifier.width(420.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Reset password", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(24.dp))
        JellioTextField(
            value = username,
            onValueChange = { username = it },
            label = "Username",
            modifier = Modifier.fillMaxWidth(),
        )
        uiState.resetStatus?.let { status ->
            Text(text = status, color = JellioTextSecondary, modifier = Modifier.padding(top = 16.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Surface(
            onClick = { onSubmit(username) },
            enabled = !uiState.isRequestingReset,
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = JellioSecondary, contentColor = JellioBg),
        ) {
            Text(text = "Send reset code", modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp))
        }
    }
}

// Real port of screens/login.js's own buildForgotPinForm(): step 2 of
// 2, the code the reader's own inbox just received plus a real new
// password to end on. A real successful redeem clears the account's
// own password server side rather than setting the one typed here
// (JellioRepository.kt's own redeemPasswordReset() header comment
// covers the two real follow up calls that makes necessary), this
// form only ever shows a generic status either way, the same real
// leak-prevention reasoning ForgotUsernameForm's own header documents.
@Composable
private fun ForgotPinForm(
    uiState: LoginUiState,
    onSubmit: (String, String, String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.width(420.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Reset password", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(24.dp))
        JellioTextField(
            value = pin,
            onValueChange = { pin = it },
            label = "Reset code",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))
        JellioTextField(
            value = newPassword,
            onValueChange = { newPassword = it },
            label = "New password",
            isPassword = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))
        JellioTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = "Confirm new password",
            isPassword = true,
            modifier = Modifier.fillMaxWidth(),
        )
        uiState.resetStatus?.let { status ->
            Text(text = status, color = JellioTextSecondary, modifier = Modifier.padding(top = 16.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Surface(
            onClick = { onSubmit(pin, newPassword, confirmPassword) },
            enabled = !uiState.isRedeemingReset,
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = JellioSecondary, contentColor = JellioBg),
        ) {
            Text(text = "Reset password", modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp))
        }
    }
}
