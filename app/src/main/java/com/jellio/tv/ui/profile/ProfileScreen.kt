package com.jellio.tv.ui.profile

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.jellio.tv.data.model.BadgeDto
import com.jellio.tv.data.model.GroupedActivityEntryDto
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.common.JellioTextField
import com.jellio.tv.ui.common.formatRelativeTime
import com.jellio.tv.ui.common.rarityColor
import com.jellio.tv.ui.common.watchActivityText
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioBorder
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary

// Real port of screens/profile.js's own renderProfile(): banner, avatar,
// name, bio, and (unless this is someone else's own private profile) a
// stats row, a badges grid and a recent activity list. userId null means
// the signed in reader's own profile, same real default JellioRoute.
// Profile's own header documents.
@Composable
fun ProfileScreen(
    session: Session,
    userId: String?,
    userImageUrl: (userId: String, tag: String?, maxWidth: Int) -> String,
    bannerUrl: (userId: String) -> String,
    itemImageUrl: (itemId: String, tag: String?, imageType: String, maxWidth: Int) -> String,
    onNavigateToDetail: (String) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val targetUserId = userId ?: session.userId
    // Real DetailScreen.kt's own header on this exact fix: nothing here
    // ever requested initial D-pad focus either, so a reader pushed
    // into this screen (View Profile from the account switcher, a Feed
    // row's own avatar) had no real target at all once landed - stuck
    // on this rail's own sidebar, unable to select or navigate anything
    // inside this screen itself.
    val contentFocusRequester = remember { FocusRequester() }
    LaunchedEffect(uiState.isLoading, uiState.error) {
        if (!uiState.isLoading && uiState.error == null) contentFocusRequester.requestFocus()
    }

    LaunchedEffect(targetUserId) { viewModel.load(session, userId) }

    Box(modifier = modifier.fillMaxSize().background(JellioBg)) {
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Loading...", color = JellioTextSecondary)
            }
            uiState.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = uiState.error ?: "Could not load this profile", color = JellioTextSecondary)
                    Surface(
                        onClick = { viewModel.retry(session, userId) },
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                        colors = ClickableSurfaceDefaults.colors(containerColor = JellioBgElevated, contentColor = JellioText, focusedContainerColor = Color.White.copy(alpha = 0.18f), focusedContentColor = JellioText),
                        modifier = Modifier.padding(top = 20.dp),
                    ) {
                        Text(text = "Retry", modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp))
                    }
                }
            }
            else -> {
                val user = uiState.user
                val profile = uiState.profile
                val achievements = uiState.achievements
                val bannerImageUrl = remember(targetUserId, uiState.bannerBustToken) { bannerUrl(targetUserId) }

                LazyColumn(modifier = Modifier.fillMaxSize().focusRequester(contentFocusRequester).focusRestorer()) {
                    item { ProfileBanner(bannerUrl = bannerImageUrl) }
                    item {
                        Column(modifier = Modifier.padding(horizontal = 48.dp, vertical = 20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = userImageUrl(targetUserId, user?.PrimaryImageTag, 200),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(72.dp).clip(CircleShape).background(JellioBgElevated),
                                )
                                Column(modifier = Modifier.padding(start = 16.dp)) {
                                    Text(text = user?.Name ?: "", style = MaterialTheme.typography.titleLarge)
                                    if (uiState.isOwner && profile?.IsPrivate == true) {
                                        Surface(
                                            onClick = {},
                                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                                            colors = ClickableSurfaceDefaults.colors(containerColor = JellioBgElevated, contentColor = JellioTextSecondary, focusedContainerColor = Color.White.copy(alpha = 0.18f), focusedContentColor = JellioText),
                                            modifier = Modifier.padding(top = 4.dp),
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                                                Icon(Icons.Filled.VisibilityOff, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Text(text = "Private", modifier = Modifier.padding(start = 4.dp), style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                            }

                            BioSection(
                                bio = profile?.Bio,
                                isOwner = uiState.isOwner,
                                isEditing = uiState.isEditingBio,
                                draft = uiState.bioDraft,
                                isSaving = uiState.isSavingBio,
                                onEdit = { viewModel.startEditingBio() },
                                onDraftChange = { viewModel.updateBioDraft(it) },
                                onSave = { viewModel.saveBio() },
                                onCancel = { viewModel.cancelEditingBio() },
                            )
                        }
                    }

                    if (achievements?.IsPrivate == true) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Filled.VisibilityOff, contentDescription = null, tint = JellioTextSecondary, modifier = Modifier.size(32.dp))
                                    Text(text = "This profile is private.", color = JellioTextSecondary, modifier = Modifier.padding(top = 12.dp))
                                }
                            }
                        }
                    } else if (achievements != null) {
                        item { StatsRow(achievements.MoviesCompleted, achievements.EpisodesCompleted, achievements.TotalCompleted, achievements.BestBingeStreak) }
                        item {
                            Text(
                                text = "Badges (${achievements.Badges.count { it.Unlocked }}/${achievements.Badges.size})",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 48.dp, top = 24.dp, bottom = 12.dp),
                            )
                        }
                        item {
                            BadgesGrid(badges = achievements.Badges, modifier = Modifier.padding(horizontal = 40.dp))
                        }
                        item {
                            Text(
                                text = "Recent activity",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 48.dp, top = 24.dp, bottom = 12.dp),
                            )
                        }
                        if (achievements.RecentActivity.isEmpty()) {
                            item {
                                Text(text = "Nothing here yet.", color = JellioTextSecondary, modifier = Modifier.padding(start = 48.dp))
                            }
                        } else {
                            items(achievements.RecentActivity) { entry ->
                                ActivityRow(
                                    entry = entry,
                                    imageUrl = itemImageUrl,
                                    onClick = { onNavigateToDetail(entry.SeriesId ?: entry.ItemId) },
                                )
                            }
                        }
                    }

                    if (uiState.isOwner) {
                        item {
                            Surface(
                                onClick = onLogout,
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                                colors = ClickableSurfaceDefaults.colors(containerColor = JellioSecondary, contentColor = JellioBg),
                                modifier = Modifier.padding(start = 48.dp, top = 32.dp, bottom = 48.dp),
                            ) {
                                Text(text = "Log Out", modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp))
                            }
                        }
                    } else {
                        item { Spacer(modifier = Modifier.height(48.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileBanner(bannerUrl: String) {
    var showFallback by remember(bannerUrl) { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 4.2f)) {
        if (showFallback) {
            Box(modifier = Modifier.fillMaxSize().background(JellioBgElevated))
        } else {
            AsyncImage(
                model = bannerUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { showFallback = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun BioSection(
    bio: String?,
    isOwner: Boolean,
    isEditing: Boolean,
    draft: String,
    isSaving: Boolean,
    onEdit: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        if (isEditing) {
            JellioTextField(
                value = draft,
                onValueChange = onDraftChange,
                label = "Add a short bio",
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.padding(top = 8.dp)) {
                Surface(
                    onClick = onSave,
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = JellioSecondary, contentColor = JellioBg),
                ) {
                    Text(text = if (isSaving) "Saving..." else "Save", modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                }
                Surface(
                    onClick = onCancel,
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = JellioBgElevated, contentColor = JellioText, focusedContainerColor = Color.White.copy(alpha = 0.18f), focusedContentColor = JellioText),
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text(text = "Cancel", modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                }
            }
        } else if (!bio.isNullOrEmpty()) {
            Text(text = bio, color = JellioTextSecondary, modifier = Modifier.clickableIfOwner(isOwner, onEdit))
        } else if (isOwner) {
            Text(
                text = "Add a short bio.",
                color = JellioTextSecondary,
                modifier = Modifier.clickableIfOwner(isOwner, onEdit),
            )
        }
    }
}

private fun Modifier.clickableIfOwner(isOwner: Boolean, onClick: () -> Unit): Modifier =
    if (isOwner) this.clickable(onClick = onClick) else this

@Composable
private fun StatsRow(movies: Int, episodes: Int, total: Int, bestBinge: Int) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 8.dp)) {
        StatTile("Movies", movies.toString(), Modifier.weight(1f))
        StatTile("Episodes", episodes.toString(), Modifier.weight(1f))
        StatTile("Total watched", total.toString(), Modifier.weight(1f))
        StatTile("Best binge", bestBinge.toString(), Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(JellioBgElevated)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(text = label, color = JellioTextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

// Real bug found live testing on device: a LazyVerticalGrid nested
// inside this screen's own outer LazyColumn item needs its own fixed
// height to lay out at all (the same real reason
// AvatarPickerOverlay.kt's own header already documents choosing
// FlowRow over exactly this), and a nested lazy list is its own
// separate real focus/scroll boundary regardless of that height being
// sized correctly: a D-pad Down off its own last real row had nowhere
// defined to go, real feedback's own "only see the first two rows,
// nothing else" and "navigating down moves into the sidebar" both
// traced to this same one real cause. FlowRow instead: no nested lazy
// list, no separate focus boundary, every badge a real part of this
// same outer LazyColumn's own real scroll and Down-navigation chain.
@Composable
private fun BadgesGrid(badges: List<BadgeDto>, modifier: Modifier = Modifier) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        badges.forEach { badge -> BadgeTile(badge, modifier = Modifier.width(120.dp)) }
    }
}

// Real feedback live: this used to be a plain Column, no Surface/
// clickable anywhere on it, so a D-pad had nothing to actually land on
// here at all. Real port of screens/profile.js's own badge tile
// hover/title tooltip: focusable now, badge.Description still its own
// real contentDescription for a screen reader, no separate action
// behind it since unlocking already happened server side.
@Composable
private fun BadgeTile(badge: BadgeDto, modifier: Modifier = Modifier) {
    val color = if (badge.Unlocked) rarityColor(badge.Rarity) else JellioBorder
    Surface(
        onClick = {},
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = color.copy(alpha = if (badge.Unlocked) 0.18f else 0.08f),
            contentColor = if (badge.Unlocked) JellioText else JellioTextSecondary,
            focusedContainerColor = Color.White.copy(alpha = 0.18f),
            focusedContentColor = JellioText,
        ),
        modifier = modifier.padding(6.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                if (badge.Unlocked) Icons.Filled.EmojiEvents else Icons.Filled.Lock,
                contentDescription = badge.Description,
                tint = if (badge.Unlocked) color else JellioTextSecondary,
            )
            Text(
                text = badge.Name ?: "",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun ActivityRow(
    entry: GroupedActivityEntryDto,
    imageUrl: (String, String?, String, Int) -> String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = JellioText,
            focusedContainerColor = Color.White.copy(alpha = 0.18f),
            focusedContentColor = JellioText,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 2.dp),
    ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var showFallback by remember(entry.ItemId) { mutableStateOf(false) }
        val posterId = entry.SeriesId ?: entry.ItemId
        if (showFallback) {
            Box(
                modifier = Modifier.size(width = 48.dp, height = 72.dp).clip(RoundedCornerShape(6.dp)).background(JellioBgElevated),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = JellioTextSecondary)
            }
        } else {
            AsyncImage(
                model = imageUrl(posterId, null, "Primary", 160),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { showFallback = true },
                modifier = Modifier.size(width = 48.dp, height = 72.dp).clip(RoundedCornerShape(6.dp)),
            )
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = watchActivityText(
                    itemType = entry.ItemType,
                    seriesName = entry.SeriesName,
                    episodeCount = entry.EpisodeCount,
                    seasonNumber = entry.SeasonNumber,
                    firstEpisodeNumber = entry.FirstEpisodeNumber,
                    lastEpisodeNumber = entry.LastEpisodeNumber,
                    itemName = entry.ItemName,
                ),
                color = JellioText,
            )
            Text(text = formatRelativeTime(entry.CompletedAtUtc), color = JellioTextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
    }
}
