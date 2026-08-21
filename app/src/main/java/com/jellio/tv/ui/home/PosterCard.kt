package com.jellio.tv.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioDanger
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioText

private val PosterWidth = 170.dp

// Mirrors components/card.js's own poster shape: a 2:3 poster, no
// text underneath (the real title only shows up in the row's own
// heading and, later, a focused card's own detail panel), the same
// real reasoning card.js's own header gives for keeping a grid dense.
//
// onOptionsClick mirrors components/card.js's own buildCardActions():
// real Watchlist/Mark Watched actions reachable without leaving the
// grid, hover/focus-revealed there, reached through a real options
// button here instead (same real reasoning DetailScreen's own
// EpisodeCard options button already documents: no D-pad equivalent
// for a hold/right-click gesture worth trusting untested). The menu
// itself (CardOptionsMenu below) is the caller's own job to render at
// its own screen root, same real reason DetailScreen's own
// EpisodeOptionsMenu is not owned by EpisodeCard either: a real
// full-screen overlay rendered from inside a LazyRow item would only
// ever fill that row's own bounds, not the real screen. null (the
// default) renders no options button at all, same real card shape
// this had before it existed.
@Composable
fun PosterCard(
    item: BaseItemDto,
    imageUrl: (BaseItemDto, String, Int) -> String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onOptionsClick: (() -> Unit)? = null,
) {
    Box(modifier = modifier.width(PosterWidth)) {
        Surface(
            onClick = onClick,
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = JellioBgElevated),
            modifier = Modifier.width(PosterWidth),
        ) {
            Box(modifier = Modifier.width(PosterWidth).aspectRatio(2f / 3f)) {
                AsyncImage(
                    model = imageUrl(item, "Primary", 400),
                    contentDescription = item.Name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                )
                // Real port of components/card.js's own paintCardState():
                // a watched checkmark badge wins outright over a progress
                // bar, the same real Played-before-PlayedPercentage order
                // that function's own branching uses. Bottom corner
                // rather than top: the options button below now owns
                // the top corner, same real reason EpisodeCard's own
                // played check moved there first.
                val userData = item.UserData
                if (userData?.Played == true) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = JellioText,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(20.dp)
                            .background(JellioSecondary, CircleShape)
                            .padding(3.dp),
                    )
                } else {
                    val percentage = userData?.PlayedPercentage
                    if (percentage != null && percentage > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(Color.White.copy(alpha = 0.25f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(minOf(100.0, percentage).toFloat() / 100f)
                                    .height(4.dp)
                                    .background(JellioSecondary),
                            )
                        }
                    }
                }
            }
        }
        if (onOptionsClick != null) {
            Surface(
                onClick = onOptionsClick,
                shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color.Black.copy(alpha = 0.55f), contentColor = JellioText),
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(28.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "Options", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// Real port of components/cardOptionsMenu.js's own real content
// split: a Continue Watching card offers Go to details/Play manually/
// Start from beginning/Remove, an Up Next card offers Go to details/
// Remove from Up Next, every other card offers Watchlist/Mark Watched,
// matched here rather than one generic list either real context has
// to squint past. Reached through a card's own options button
// (PosterCard/LandscapeCard's onOptionsClick) rather than that file's
// own held-remote-button gesture, same real EpisodeOptionsMenu shape
// DetailScreen already established for the exact same real D-pad
// constraint. Rendered by the caller at its own screen root, not by
// PosterCard/LandscapeCard themselves: see PosterCard's own header
// comment for why.
@Composable
fun CardOptionsMenu(
    item: BaseItemDto,
    continueWatching: Boolean = false,
    upNext: Boolean = false,
    onGoToDetails: (() -> Unit)? = null,
    onPlayManually: (() -> Unit)? = null,
    onStartOver: (() -> Unit)? = null,
    onRemoveFromRow: (() -> Unit)? = null,
    onToggleWatchlist: (() -> Unit)? = null,
    onToggleWatched: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val isWatchlisted = item.UserData?.IsFavorite == true
    val isPlayed = item.UserData?.Played == true
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 260.dp)
                .background(JellioBgElevated, RoundedCornerShape(16.dp))
                .padding(vertical = 12.dp),
        ) {
            Text(
                text = item.Name.orEmpty(),
                color = JellioText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            when {
                continueWatching -> {
                    onGoToDetails?.let { CardOptionsMenuRow(label = "Go to details", onClick = { it(); onDismiss() }) }
                    onPlayManually?.let { CardOptionsMenuRow(label = "Play manually", onClick = { it(); onDismiss() }) }
                    onStartOver?.let { CardOptionsMenuRow(label = "Start from beginning", onClick = { it(); onDismiss() }) }
                    onRemoveFromRow?.let { CardOptionsMenuRow(label = "Remove from Continue Watching", onClick = { it(); onDismiss() }, danger = true) }
                }
                upNext -> {
                    onGoToDetails?.let { CardOptionsMenuRow(label = "Go to details", onClick = { it(); onDismiss() }) }
                    // Real gap components/cardOptionsMenu.js's own
                    // toggleWatched() header documents: Jellyfin has no
                    // endpoint that just hides one title from NextUp on
                    // its own, marking the episode played is the only
                    // real call that also drops it off this row.
                    onRemoveFromRow?.let { CardOptionsMenuRow(label = "Remove from Up Next", onClick = { it(); onDismiss() }, danger = true) }
                }
                else -> {
                    if (onToggleWatchlist != null) {
                        CardOptionsMenuRow(label = if (isWatchlisted) "Remove from Watchlist" else "Add to Watchlist", onClick = { onToggleWatchlist(); onDismiss() })
                    }
                    if (onToggleWatched != null) {
                        CardOptionsMenuRow(label = if (isPlayed) "Mark as unwatched" else "Mark as watched", onClick = { onToggleWatched(); onDismiss() })
                    }
                }
            }
        }
    }
}

@Composable
private fun CardOptionsMenuRow(label: String, onClick: () -> Unit, danger: Boolean = false) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(0.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, contentColor = if (danger) JellioDanger else JellioText),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = label, modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp))
    }
}
