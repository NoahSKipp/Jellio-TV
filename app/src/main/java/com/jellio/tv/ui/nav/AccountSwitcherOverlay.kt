package com.jellio.tv.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
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
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.auth.RememberedProfile
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioDanger
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary

// Real port of components/accountSwitcher.js's own openAccountSwitcher():
// opened from this rail's own Profile row instead of navigating
// straight to the full ProfileScreen, real feedback's own explicit
// ask matching real desktop parity - Settings and this row used to
// both just reach #/account with no real second job of their own.
// A small floating panel over whatever screen was already open rather
// than a full screen of its own, same real reason that file's own
// header gives: switching mid-browse should not need leaving the page
// first.
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AccountSwitcherOverlay(
    session: Session,
    onDismiss: () -> Unit,
    onViewProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountSwitcherViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(session.userId) { viewModel.load(session) }
    BackHandler(onBack = onDismiss)

    // Real bug every other real overlay in this app already had to fix
    // the same real way: claim initial D-pad focus on open, and trap
    // exit so it cannot wander back out into whatever real screen sits
    // behind this scrim.
    val viewProfileFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { viewProfileFocusRequester.requestFocus() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusProperties { exit = { FocusRequester.Cancel } }
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .fillMaxHeight(0.8f)
                .background(JellioBgElevated, RoundedCornerShape(16.dp))
                .padding(32.dp)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = {}),
        ) {
            // Real components/accountSwitcher.js's own header: the
            // signed in reader's own avatar/name plus View Profile/
            // Settings/Sign Out, sitting above the grid rather than
            // inside it since there is no tile of its own for the
            // reader's own currently signed in account to anchor to.
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = viewModel.avatarUrl(session, session.userId, null),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp).clip(CircleShape).background(JellioBg),
                )
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(text = session.userName, style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                        Surface(
                            onClick = { onDismiss(); onViewProfile() },
                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = JellioBg,
                                contentColor = JellioText,
                                focusedContainerColor = Color.White.copy(alpha = 0.18f),
                                focusedContentColor = JellioText,
                            ),
                            modifier = Modifier.focusRequester(viewProfileFocusRequester),
                        ) {
                            Text(text = "View Profile", modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                        }
                        Surface(
                            onClick = { onDismiss(); onOpenSettings() },
                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = JellioBg,
                                contentColor = JellioText,
                                focusedContainerColor = Color.White.copy(alpha = 0.18f),
                                focusedContentColor = JellioText,
                            ),
                        ) {
                            Text(text = "Settings", modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                        }
                        Surface(
                            onClick = onSignOut,
                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                            colors = ClickableSurfaceDefaults.colors(containerColor = JellioSecondary, contentColor = JellioBg),
                        ) {
                            Text(text = "Sign Out", modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                        }
                    }
                }
            }

            Text(
                text = "Switch Profile",
                style = MaterialTheme.typography.titleMedium,
                color = JellioTextSecondary,
                modifier = Modifier.padding(top = 28.dp, bottom = 12.dp),
            )

            if (uiState.isLoading) {
                Text(text = "Loading...", color = JellioTextSecondary)
            } else if (uiState.remembered.isEmpty() && uiState.publicOnly.isEmpty()) {
                Text(text = "No other profiles on this server.", color = JellioTextSecondary)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(uiState.remembered, key = { "r:" + it.userId }) { profile ->
                        SwitcherTile(
                            name = profile.name,
                            imageUrl = viewModel.avatarUrl(session, profile.userId, profile.primaryImageTag),
                            busy = uiState.busyUserId == profile.userId,
                            onClick = { viewModel.quickSignIn(session, profile.userId) },
                        )
                    }
                    items(uiState.publicOnly, key = { "p:" + it.Id }) { user ->
                        SwitcherTile(
                            name = user.Name.orEmpty(),
                            imageUrl = user.Id?.let { viewModel.avatarUrl(session, it, user.PrimaryImageTag) },
                            busy = uiState.busyUserId == user.Id,
                            onClick = { viewModel.selectPublicUser(session, user, onNeedsPassword = onSignOut) },
                        )
                    }
                    item(key = "add") {
                        SwitcherTile(name = "Other user", imageUrl = null, busy = false, onClick = onSignOut, isAddTile = true)
                    }
                }
            }

            uiState.error?.let { error ->
                Text(text = error, color = JellioDanger, modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}

@Composable
private fun SwitcherTile(
    name: String,
    imageUrl: String?,
    busy: Boolean,
    onClick: () -> Unit,
    isAddTile: Boolean = false,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            enabled = !busy,
            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = JellioBg,
                contentColor = JellioText,
                focusedContainerColor = Color.White.copy(alpha = 0.18f),
                focusedContentColor = JellioText,
            ),
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
        Text(
            text = name,
            color = JellioText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp).width(112.dp),
        )
    }
}
