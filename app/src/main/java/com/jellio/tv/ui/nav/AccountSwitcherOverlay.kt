package com.jellio.tv.ui.nav

import com.jellio.tv.ui.common.dismissOnBack
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioDanger
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary

@OptIn(ExperimentalComposeUiApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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

    val viewProfileFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { viewProfileFocusRequester.requestFocus() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .dismissOnBack(onDismiss)
            .background(Color.Black.copy(alpha = 0.7f))
            // A tap outside closes it; not clickable, which would make the
            // scrim itself a focus stop.
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            // onExit only traps focus on a focus group; without it D-pad
            // moves walked out onto the screen underneath.
            .focusProperties { onExit = { FocusRequester.Cancel } }
            .focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .wrapContentHeight()
                .background(JellioBgElevated, RoundedCornerShape(16.dp))
                .padding(32.dp)
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = viewModel.avatarUrl(session, session.userId, null),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp).clip(CircleShape).background(JellioBg),
                )
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(text = session.userName, style = MaterialTheme.typography.titleLarge)
                    // Wraps to a second line rather than squeezing the last
                    // button down to a sliver when they don't all fit.
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
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
                            Text(text = com.jellio.tv.ui.i18n.string("settings_view_profile"), modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
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
                            Text(text = com.jellio.tv.ui.i18n.string("nav_settings"), modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                        }
                        Surface(
                            onClick = onSignOut,
                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                            colors = com.jellio.tv.ui.common.dangerButtonColors(),
                        ) {
                            Text(text = com.jellio.tv.ui.i18n.string("nav_log_out"), modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                        }
                    }
                }
            }

            Text(
                text = com.jellio.tv.ui.i18n.string("nav_switch_account"),
                style = MaterialTheme.typography.titleMedium,
                color = JellioTextSecondary,
                modifier = Modifier.padding(top = 28.dp, bottom = 12.dp),
            )

            uiState.error?.let { error ->
                Text(text = error, color = JellioDanger, modifier = Modifier.padding(bottom = 12.dp))
            }

            if (uiState.isLoading) {
                Text(text = com.jellio.tv.ui.i18n.string("status_loading"), color = JellioTextSecondary)
            } else if (uiState.remembered.isEmpty() && uiState.publicOnly.isEmpty()) {
                Text(text = com.jellio.tv.ui.i18n.string("account_no_other_profiles"), color = JellioTextSecondary)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    modifier = Modifier.heightIn(max = 300.dp),
                ) {
                    items(uiState.remembered, key = { "r:" + it.userId }) { profile ->
                        SwitcherTile(
                            name = profile.name,
                            imageUrl = viewModel.avatarUrl(session, profile.userId, profile.primaryImageTag),
                            busy = uiState.busyUserId == profile.userId,
                            onClick = { viewModel.quickSignIn(session, profile.userId, onNeedsPassword = onSignOut) },
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
                        SwitcherTile(name = com.jellio.tv.ui.i18n.string("auth_other_user"), imageUrl = null, busy = false, onClick = onSignOut, isAddTile = true)
                    }
                }
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
            // Stays enabled while signing in: a disabled tile drops focus,
            // which then landed outside the switcher.
            onClick = { if (!busy) onClick() },
            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = JellioBg,
                contentColor = JellioText,
                focusedContainerColor = Color.White.copy(alpha = 0.18f),
                focusedContentColor = JellioText,
            ),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = androidx.tv.material3.Border(
                    border = androidx.compose.foundation.BorderStroke(3.dp, Color.White),
                    shape = CircleShape
                )
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
            if (busy) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                    com.jellio.tv.ui.common.ScreenSpinner(size = 40.dp)
                }
            }
        }
        Text(
            text = name,
            color = JellioText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
    }
}
