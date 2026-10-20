package com.jellio.tv.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tv
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Border
import com.jellio.tv.data.prefs.AppLanguage
import com.jellio.tv.data.prefs.DisplayPreferences
import com.jellio.tv.ui.i18n.string
import com.jellio.tv.ui.theme.JellioBorder
import com.jellio.tv.ui.theme.JellioRarityRare
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
import androidx.compose.material.icons.filled.Sync
import androidx.compose.ui.unit.sp
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
    BackHandler(enabled = uiState.mode == LoginMode.QUICK_CONNECT) {
        viewModel.cancelQuickConnect()
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (uiState.mode) {
            // A spinner rather than the form while the server is checked
            // (trying https and http can take a few seconds).
            LoginMode.CHECKING -> com.jellio.tv.ui.common.ScreenSpinner()
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
                onQuickConnect = { viewModel.startQuickConnect() },
                avatarUrl = { userId, tag -> viewModel.avatarUrl(userId, tag) },
            )
            LoginMode.MANUAL -> ManualLoginForm(
                uiState = uiState,
                onLogin = { server, username, password -> viewModel.login(server, username, password) },
                onForgotPassword = { username -> viewModel.showForgotPassword(username) },
                onQuickConnect = { viewModel.startQuickConnect() },
            )
            LoginMode.FORGOT_USERNAME -> ForgotUsernameForm(
                uiState = uiState,
                onSubmit = { username -> viewModel.requestPasswordReset(username) },
            )
            LoginMode.FORGOT_PIN -> ForgotPinForm(
                uiState = uiState,
                onSubmit = { pin, newPassword, confirmPassword -> viewModel.redeemPasswordReset(pin, newPassword, confirmPassword) },
            )
            LoginMode.QUICK_CONNECT -> QuickConnectForm(
                uiState = uiState,
                onRetry = { viewModel.startQuickConnect() },
                onBack = { viewModel.cancelQuickConnect() },
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
    var currentLanguage by remember { mutableStateOf(DisplayPreferences.displayLanguage) }

    Column(
        modifier = Modifier
            .widthIn(max = 560.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(JellioBgElevated)
            .border(1.dp, JellioBorder, RoundedCornerShape(24.dp))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Header: Logo Mark, Step Badge, and Quick Language Switcher
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(listOf(JellioRarityRare, JellioSecondary))
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tv,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column {
                    Text(
                        text = "Jellio TV",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = JellioText,
                    )
                    Text(
                        text = string("onboarding_welcome_title") + " • " + string("auth_step_server"),
                        style = MaterialTheme.typography.bodySmall,
                        color = JellioTextSecondary,
                    )
                }
            }

            // Language quick toggle pill
            val context = LocalContext.current
            Surface(
                onClick = {
                    val allLangs = AppLanguage.entries
                    val next = allLangs[(allLangs.indexOf(currentLanguage) + 1) % allLangs.size]
                    DisplayPreferences.setDisplayLanguage(context, next)
                    currentLanguage = next
                },
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.06f),
                    contentColor = JellioText,
                    focusedContainerColor = Color.White.copy(alpha = 0.18f),
                    focusedContentColor = JellioText,
                ),
                border = ClickableSurfaceDefaults.border(
                    focusedBorder = Border(BorderStroke(2.dp, JellioSecondary)),
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Translate,
                        contentDescription = null,
                        tint = JellioSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = currentLanguage.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = string("auth_connect_title"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = JellioText,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = string("auth_connect_subtitle"),
            style = MaterialTheme.typography.bodyMedium,
            color = JellioTextSecondary,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 20.dp),
        )

        JellioTextField(
            value = serverAddress,
            onValueChange = { serverAddress = it },
            label = string("auth_server_address") + " (" + string("auth_server_hint") + ")",
            modifier = Modifier.fillMaxWidth(),
        )

        // Protocol autofill explanation callout
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.04f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = JellioSecondary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = string("auth_autofill_tip"),
                    style = MaterialTheme.typography.bodySmall,
                    color = JellioTextSecondary,
                )
            }
        }

        uiState.error?.let { error ->
            Text(
                text = error,
                color = JellioDanger,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Surface(
            onClick = { onContinue(serverAddress) },
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
            colors = com.jellio.tv.ui.common.primaryButtonColors(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
            ) {
                Text(
                    text = string("onboarding_next"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
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
    onQuickConnect: () -> Unit,
    avatarUrl: (String, String?) -> String,
) {
    Column(
        modifier = Modifier.width(760.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = string("auth_who_is_watching"), style = MaterialTheme.typography.titleLarge)
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
                ProfileTile(name = string("auth_other_user"), imageUrl = null, busy = false, onClick = onAddOther, onRemove = null, isAddTile = true)
            }
            item(key = "quick_connect") {
                ProfileTile(name = string("auth_quick_connect_tile"), imageUrl = null, busy = false, onClick = onQuickConnect, onRemove = null, isQuickConnectTile = true)
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
    isQuickConnectTile: Boolean = false,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Surface(
                onClick = onClick,
                enabled = !busy,
                shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isQuickConnectTile) Color.White.copy(alpha = 0.08f) else JellioBgElevated,
                    contentColor = if (isQuickConnectTile) JellioSecondary else JellioText,
                    focusedContainerColor = if (isQuickConnectTile) JellioSecondary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.18f),
                    focusedContentColor = JellioText,
                ),
                border = ClickableSurfaceDefaults.border(
                    focusedBorder = Border(BorderStroke(2.dp, JellioSecondary)),
                ),
                modifier = Modifier.size(112.dp),
            ) {
                if (isQuickConnectTile) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Filled.Sync, contentDescription = null, tint = JellioSecondary, modifier = Modifier.size(36.dp))
                    }
                } else if (isAddTile) {
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
                        Icon(imageVector = Icons.Filled.Close, contentDescription = com.jellio.tv.ui.i18n.Strings.get("action_forget_profile"), modifier = Modifier.size(14.dp))
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
    onQuickConnect: () -> Unit,
) {
    var serverAddress by remember(uiState.serverAddress) { mutableStateOf(uiState.serverAddress) }
    var username by remember(uiState.manualPrefillUsername) { mutableStateOf(uiState.manualPrefillUsername) }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .widthIn(max = 560.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(JellioBgElevated)
            .border(1.dp, JellioBorder, RoundedCornerShape(24.dp))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(listOf(JellioRarityRare, JellioSecondary))
                    ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Tv,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column {
                Text(
                    text = "Jellio TV",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = JellioText,
                )
                Text(
                    text = string("auth_sign_in"),
                    style = MaterialTheme.typography.bodySmall,
                    color = JellioTextSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        JellioTextField(
            value = serverAddress,
            onValueChange = { serverAddress = it },
            label = string("auth_server_address") + " (" + string("auth_server_hint") + ")",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(14.dp))
        JellioTextField(
            value = username,
            onValueChange = { username = it },
            label = string("auth_username"),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(14.dp))
        JellioTextField(
            value = password,
            onValueChange = { password = it },
            label = string("auth_password"),
            isPassword = true,
            modifier = Modifier.fillMaxWidth(),
        )
        uiState.error?.let { error ->
            Text(
                text = error,
                color = JellioDanger,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Surface(
            onClick = { onLogin(serverAddress, username, password) },
            enabled = !uiState.isLoading,
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
            colors = com.jellio.tv.ui.common.primaryButtonColors(),
        ) {
            Text(
                text = if (uiState.isLoading) string("auth_sign_in") + "..." else string("auth_sign_in"),
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
            )
        }
        Surface(
            onClick = onQuickConnect,
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.08f), contentColor = JellioText, focusedContainerColor = Color.White.copy(alpha = 0.2f), focusedContentColor = JellioText),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, JellioSecondary))),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                Icon(imageVector = Icons.Filled.Sync, contentDescription = null, tint = JellioSecondary, modifier = Modifier.size(16.dp))
                Text(text = string("auth_sign_in_quick_connect"), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Surface(
            onClick = { onForgotPassword(username) },
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, contentColor = JellioTextSecondary),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(text = string("auth_forgot_password"), modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
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
        Text(text = com.jellio.tv.ui.i18n.Strings.get("auth_reset_password"), style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(24.dp))
        JellioTextField(
            value = username,
            onValueChange = { username = it },
            label = com.jellio.tv.ui.i18n.string("auth_username"),
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
            colors = com.jellio.tv.ui.common.primaryButtonColors(),
        ) {
            Text(text = com.jellio.tv.ui.i18n.Strings.get("auth_send_reset_code"), modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp))
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
        Text(text = com.jellio.tv.ui.i18n.Strings.get("auth_reset_password"), style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(24.dp))
        JellioTextField(
            value = pin,
            onValueChange = { pin = it },
            label = com.jellio.tv.ui.i18n.string("auth_reset_code"),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))
        JellioTextField(
            value = newPassword,
            onValueChange = { newPassword = it },
            label = com.jellio.tv.ui.i18n.string("settings_password_new"),
            isPassword = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))
        JellioTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = com.jellio.tv.ui.i18n.string("settings_password_confirm"),
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
            colors = com.jellio.tv.ui.common.primaryButtonColors(),
        ) {
            Text(text = com.jellio.tv.ui.i18n.Strings.get("auth_reset_password"), modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp))
        }
    }
}
@Composable
private fun QuickConnectForm(
    uiState: LoginUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .widthIn(max = 580.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(JellioBgElevated)
            .border(1.dp, JellioBorder, RoundedCornerShape(24.dp))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(listOf(JellioRarityRare, JellioSecondary))
                    ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Tv,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column {
                Text(
                    text = "Jellio TV",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = JellioText,
                )
                Text(
                    text = string("auth_quick_connect"),
                    style = MaterialTheme.typography.bodySmall,
                    color = JellioTextSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = string("auth_sign_in_quick_connect"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = JellioText,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = string("auth_quick_connect_desc"),
            style = MaterialTheme.typography.bodyMedium,
            color = JellioTextSecondary,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 20.dp),
        )

        val code = uiState.quickConnectCode
        if (code != null) {
            val formattedCode = if (code.length == 6) "${code.take(3)} ${code.drop(3)}" else code
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, JellioSecondary.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(vertical = 20.dp, horizontal = 24.dp),
            ) {
                Text(
                    text = formattedCode,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = JellioSecondary,
                    letterSpacing = 6.sp,
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = JellioSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = string("auth_quick_connect_instructions"),
                        style = MaterialTheme.typography.bodySmall,
                        color = JellioTextSecondary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Sync,
                    contentDescription = null,
                    tint = JellioSecondary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = string("auth_quick_connect_waiting"),
                    style = MaterialTheme.typography.bodySmall,
                    color = JellioTextSecondary,
                )
            }
        } else if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                contentAlignment = Alignment.Center,
            ) {
                com.jellio.tv.ui.common.ScreenSpinner()
            }
        }

        uiState.error?.let { err ->
            Text(
                text = err,
                color = JellioDanger,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            )
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                onClick = onRetry,
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                colors = com.jellio.tv.ui.common.primaryButtonColors(),
            ) {
                Text(
                    text = string("action_retry"),
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 10.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Surface(
            onClick = onBack,
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.08f),
                contentColor = JellioText,
                focusedContainerColor = Color.White.copy(alpha = 0.2f),
                focusedContentColor = JellioText,
            ),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, JellioSecondary))),
        ) {
            Text(
                text = string("action_cancel"),
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 10.dp),
            )
        }
    }
}