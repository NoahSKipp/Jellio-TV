package com.jellio.tv.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioDanger
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.scaled
import kotlinx.coroutines.delay
import java.util.Locale

// Real gap found live right after prefetch-on-focus shipped: D-pad
// scrolling through a long row focused, and so fired a prefetch for,
// every card passed over on the way to the one a reader actually
// stopped on: wasted round trips against their own addon/debrid/TMDb
// rate limits for titles nobody was ever about to open. Mirrors
// components/card.js's own PREFETCH_DEBOUNCE_MS: only fires once focus
// actually lingers past this, not on every transient landing.
// Only a card the viewer stays on: moving along a row never prefetches.
private const val PREFETCH_DEBOUNCE_MS = 600L

// Sized to 126.dp x 189.dp standard portrait cards, expanding to 336.dp
// (16:9 banner) when focused.
internal val PosterWidth = 126.dp

// Ordered fallback list of candidate image URLs for a poster card, matching
// Jellio Web components/card.js cardImageCandidates(): tries tagged Primary,
// untagged Primary (bypassing stale tags or transient first-hit remote fetch
// timeouts), Series Primary, Parent Thumb, Parent Primary, Album Primary,
// Thumb, Backdrop, and Parent Backdrop.
internal fun posterImageCandidates(
    item: BaseItemDto,
    imageUrl: (BaseItemDto, String, Int) -> String,
    maxWidth: Int = 400,
): List<String> {
    val urls = LinkedHashSet<String>()
    fun buildUrl(targetId: String, tag: String?, type: String): String {
        val synthetic = BaseItemDto(
            Id = targetId,
            ImageTags = if (!tag.isNullOrEmpty() && type != "Backdrop") mapOf(type to tag) else emptyMap(),
            BackdropImageTags = if (!tag.isNullOrEmpty() && type == "Backdrop") listOf(tag) else emptyList(),
        )
        return imageUrl(synthetic, type, maxWidth)
    }
    fun push(targetId: String?, tag: String?, type: String = "Primary", includeUntagged: Boolean = true) {
        if (targetId.isNullOrBlank()) return
        if (!tag.isNullOrBlank()) {
            val tagged = buildUrl(targetId, tag, type)
            if (tagged.isNotBlank()) urls.add(tagged)
        }
        if (includeUntagged) {
            val untagged = buildUrl(targetId, null, type)
            if (untagged.isNotBlank()) urls.add(untagged)
        }
    }

    val primaryTag = item.ImageTags?.get("Primary") ?: item.PrimaryImageTag
    push(item.Id, primaryTag, "Primary", includeUntagged = true)

    val seriesPrimaryTag = item.SeriesPrimaryImageTag ?: item.ImageTags?.get("SeriesPrimary")
    push(item.SeriesId, seriesPrimaryTag, "Primary", includeUntagged = true)

    push(item.ParentThumbItemId, item.ParentThumbImageTag, "Thumb", includeUntagged = true)

    val parentPrimaryId = item.ParentPrimaryImageItemId ?: item.ParentId
    push(parentPrimaryId, item.ParentPrimaryImageTag, "Primary", includeUntagged = true)

    push(item.AlbumId, item.AlbumPrimaryImageTag, "Primary", includeUntagged = true)

    val thumbTag = item.ImageTags?.get("Thumb") ?: item.ThumbImageTag
    if (!thumbTag.isNullOrBlank()) {
        push(item.Id, thumbTag, "Thumb", includeUntagged = true)
    }

    val backdropTag = item.BackdropImageTags?.firstOrNull() ?: item.ImageTags?.get("Backdrop")
    if (!backdropTag.isNullOrBlank()) {
        push(item.Id, backdropTag, "Backdrop", includeUntagged = true)
    }

    val parentBackdropTag = item.ParentBackdropImageTags?.firstOrNull()
    if (!item.ParentBackdropItemId.isNullOrBlank() && !parentBackdropTag.isNullOrBlank()) {
        push(item.ParentBackdropItemId, parentBackdropTag, "Backdrop", includeUntagged = true)
    }

    push(item.Id, null, "Thumb", includeUntagged = true)
    push(item.Id, null, "Backdrop", includeUntagged = true)

    return urls.toList()
}

// Ordered candidate image URLs for the 16:9 backdrop view of an expanded poster card.
internal fun posterBackdropCandidates(
    item: BaseItemDto,
    imageUrl: (BaseItemDto, String, Int) -> String,
    maxWidth: Int = 800,
): List<String> {
    val urls = LinkedHashSet<String>()
    fun buildUrl(targetId: String, tag: String?, type: String): String {
        val synthetic = BaseItemDto(
            Id = targetId,
            BackdropImageTags = if (!tag.isNullOrEmpty() && type == "Backdrop") listOf(tag) else emptyList(),
            ImageTags = if (!tag.isNullOrEmpty() && type != "Backdrop") mapOf(type to tag) else emptyMap(),
        )
        return imageUrl(synthetic, type, maxWidth)
    }
    fun push(targetId: String?, tag: String?, type: String = "Backdrop") {
        if (targetId.isNullOrBlank()) return
        if (!tag.isNullOrBlank()) {
            val tagged = buildUrl(targetId, tag, type)
            if (tagged.isNotBlank()) urls.add(tagged)
        }
        val untagged = buildUrl(targetId, null, type)
        if (untagged.isNotBlank()) urls.add(untagged)
    }

    val backdropTag = item.BackdropImageTags?.firstOrNull() ?: item.ImageTags?.get("Backdrop")
    push(item.Id, backdropTag, "Backdrop")

    val parentBackdropTag = item.ParentBackdropImageTags?.firstOrNull()
    if (!item.ParentBackdropItemId.isNullOrBlank()) {
        push(item.ParentBackdropItemId, parentBackdropTag, "Backdrop")
    }
    if (!item.SeriesId.isNullOrBlank()) {
        push(item.SeriesId, null, "Backdrop")
    }

    val thumbTag = item.ImageTags?.get("Thumb") ?: item.ThumbImageTag
    push(item.Id, thumbTag, "Thumb")
    val primaryTag = item.ImageTags?.get("Primary") ?: item.PrimaryImageTag
    push(item.Id, primaryTag, "Primary")

    return urls.toList()
}

// Ordered candidate image URLs for the clear logo of an item.
internal fun posterLogoCandidates(
    item: BaseItemDto,
    imageUrl: (BaseItemDto, String, Int) -> String,
    maxWidth: Int = 400,
): List<String> {
    val urls = LinkedHashSet<String>()
    fun buildUrl(targetId: String, tag: String?, type: String): String {
        val synthetic = BaseItemDto(
            Id = targetId,
            ImageTags = if (!tag.isNullOrEmpty()) mapOf(type to tag) else emptyMap(),
        )
        return imageUrl(synthetic, type, maxWidth)
    }
    fun push(targetId: String?, tag: String?, type: String = "Logo") {
        if (targetId.isNullOrBlank()) return
        if (!tag.isNullOrBlank()) {
            val tagged = buildUrl(targetId, tag, type)
            if (tagged.isNotBlank()) urls.add(tagged)
        }
        val untagged = buildUrl(targetId, null, type)
        if (untagged.isNotBlank()) urls.add(untagged)
    }

    val logoTag = item.ImageTags?.get("Logo")
    push(item.Id, logoTag, "Logo")

    if (!item.ParentLogoItemId.isNullOrBlank()) {
        push(item.ParentLogoItemId, item.ParentLogoImageTag, "Logo")
    }
    if (!item.SeriesId.isNullOrBlank()) {
        push(item.SeriesId, null, "Logo")
    }

    return urls.toList()
}

// Compact portrait poster card (126.dp x 189.dp) that expands into a 16:9 banner card
// (336.dp wide) after a lingering focus delay, displaying the backdrop image and ClearLogo
// with a clean white focus ring. Moving away collapses the card back down immediately.
@Composable
fun PosterCard(
    item: BaseItemDto,
    imageUrl: (BaseItemDto, String, Int) -> String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onOptionsClick: (() -> Unit)? = null,
    userImageUrl: ((String, String?) -> String)? = null,
    canExpand: Boolean = true,
    onFocus: ((BaseItemDto) -> Unit)? = null,
) {
    val baseWidth = PosterWidth.scaled()
    val baseHeight = (PosterWidth * 1.5f).scaled()
    val expandedWidth = (baseHeight * (16f / 9f))
    val prefetch = LocalGelatoPrefetch.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var isFocused by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(isFocused, canExpand) {
        if (!isFocused || !canExpand) {
            isExpanded = false
            return@LaunchedEffect
        }
        isExpanded = false
        delay(450L)
        if (isFocused && canExpand) {
            isExpanded = true
        }
    }

    LaunchedEffect(isFocused, item.Id) {
        if (isFocused) {
            onFocus?.invoke(item)
            delay(PREFETCH_DEBOUNCE_MS)
            if (!com.jellio.tv.ui.perf.ScrollActivity.scrolling) {
                prefetch(item)
                if (!item.BackdropImageTags.isNullOrEmpty()) {
                    coil3.SingletonImageLoader.get(context).enqueue(
                        coil3.request.ImageRequest.Builder(context)
                            .data(imageUrl(item, "Backdrop", 1920))
                            .memoryCachePolicy(coil3.request.CachePolicy.DISABLED)
                            .size(640, 360)
                            .build(),
                    )
                }
            }
        }
    }

    val targetWidth = if (isExpanded) expandedWidth else baseWidth
    val animatedWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "posterCardWidth",
    )

    val candidates = remember(item, imageUrl) { posterImageCandidates(item, imageUrl, 400) }
    var candidateIndex by remember(item.Id, candidates) { androidx.compose.runtime.mutableIntStateOf(0) }
    val activeUrl = candidates.getOrNull(candidateIndex)

    val backdropCandidates = remember(item, imageUrl) { posterBackdropCandidates(item, imageUrl, 800) }
    var backdropIndex by remember(item.Id, backdropCandidates) { androidx.compose.runtime.mutableIntStateOf(0) }
    val activeBackdropUrl = backdropCandidates.getOrNull(backdropIndex) ?: activeUrl

    val logoCandidates = remember(item, imageUrl) { posterLogoCandidates(item, imageUrl, 300) }
    var logoIndex by remember(item.Id, logoCandidates) { androidx.compose.runtime.mutableIntStateOf(0) }
    val activeLogoUrl = logoCandidates.getOrNull(logoIndex)
    var logoLoadFailed by remember(item.Id) { mutableStateOf(false) }

    Box(modifier = modifier.width(animatedWidth)) {
        Surface(
            onClick = onClick,
            onLongClick = onOptionsClick,
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = JellioBgElevated,
                contentColor = JellioText,
                focusedContainerColor = Color.White.copy(alpha = 0.28f),
                focusedContentColor = JellioText,
            ),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(2.dp, Color.White),
                    shape = RoundedCornerShape(12.dp),
                )
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
            modifier = Modifier
                .width(animatedWidth)
                .height(baseHeight)
                .onFocusChanged { state -> isFocused = state.isFocused },
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isExpanded) {
                    if (activeBackdropUrl != null) {
                        AsyncImage(
                            model = activeBackdropUrl,
                            contentDescription = item.Name,
                            contentScale = ContentScale.Crop,
                            onError = {
                                if (backdropIndex < backdropCandidates.size - 1) {
                                    backdropIndex += 1
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    // Bottom gradient overlay
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.65f)
                            .align(Alignment.BottomStart)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.85f),
                                    )
                                )
                            )
                    )

                    // Clearlogo or Title overlay at bottom-left
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 12.dp, bottom = 10.dp, end = 12.dp)
                            .fillMaxWidth(0.70f)
                    ) {
                        if (!activeLogoUrl.isNullOrBlank() && !logoLoadFailed) {
                            AsyncImage(
                                model = activeLogoUrl,
                                contentDescription = item.Name,
                                onError = {
                                    if (logoIndex + 1 < logoCandidates.size) {
                                        logoIndex += 1
                                    } else {
                                        logoLoadFailed = true
                                    }
                                },
                                contentScale = ContentScale.Fit,
                                alignment = Alignment.BottomStart,
                                modifier = Modifier
                                    .height(36.dp.scaled())
                                    .fillMaxWidth(),
                            )
                        } else {
                            Text(
                                text = item.Name ?: item.SeriesName ?: "",
                                color = Color.White,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                } else {
                    if (activeUrl != null) {
                        AsyncImage(
                            model = activeUrl,
                            contentDescription = item.Name,
                            contentScale = ContentScale.Crop,
                            onError = {
                                if (candidateIndex < candidates.size - 1) {
                                    candidateIndex += 1
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(JellioBgElevated)
                                .padding(12.dp),
                        ) {
                            Text(
                                text = item.Name ?: item.SeriesName ?: "",
                                color = JellioText.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                    val watchers = item.Watchers
                    if (!watchers.isNullOrEmpty()) {
                        WatchersBadge(
                            watchers = watchers,
                            userImageUrl = userImageUrl,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp),
                        )
                    }
                    item.CommunityRating?.let { rating ->
                        Text(
                            text = String.format(Locale.US, "%.1f ★", rating),
                            color = JellioText,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .align(if (!watchers.isNullOrEmpty()) Alignment.TopStart else Alignment.TopEnd)
                                .padding(6.dp)
                                .background(JellioBg.copy(alpha = 0.78f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    val userData = item.UserData
                    if (userData?.Played == true) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = JellioBg,
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
    canDelete: Boolean = false,
    onGoToDetails: (() -> Unit)? = null,
    onPlayManually: (() -> Unit)? = null,
    onStartOver: (() -> Unit)? = null,
    onRemoveFromRow: (() -> Unit)? = null,
    onRemoveShowFromRow: (() -> Unit)? = null,
    onToggleWatchlist: (() -> Unit)? = null,
    onToggleWatched: (() -> Unit)? = null,
    onDeleteItem: (() -> Unit)? = null,
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
                    // Real port of components/cardOptionsMenu.js's own
                    // "Remove Show from Up Next": the row entry above
                    // only ever marks this one episode played, which
                    // just advances the same series to its own next
                    // episode on the row's very next fetch, not
                    // actually leaving it. This hides the whole series
                    // server side instead (Controllers/
                    // NextUpHiddenController.cs), a real series stays
                    // gone across a reload rather than resurfacing
                    // under its next episode.
                    onRemoveShowFromRow?.let { CardOptionsMenuRow(label = "Remove Show from Up Next", onClick = { it(); onDismiss() }, danger = true) }
                }
                else -> {
                    if (onToggleWatchlist != null) {
                        CardOptionsMenuRow(label = if (isWatchlisted) "Remove from Watchlist" else "Add to Watchlist", onClick = { onToggleWatchlist(); onDismiss() })
                    }
                    if (onToggleWatched != null) {
                        CardOptionsMenuRow(label = if (isPlayed) "Mark as unwatched" else "Mark as watched", onClick = { onToggleWatched(); onDismiss() })
                    }
                    // Real gate components/cardOptionsMenu.js's own
                    // header documents: canDelete is decided by the
                    // caller from the signed in user's own real
                    // Policy.IsAdministrator/EnableContentDeletion,
                    // fetched once for the whole screen rather than
                    // per card. Real Remove from Library confirmation
                    // itself is the caller's own job too
                    // (RemoveFromLibraryConfirm below), same real two
                    // step flow that file's own window.confirm() gate
                    // gives before ever calling deleteItem().
                    if (canDelete && onDeleteItem != null) {
                        CardOptionsMenuRow(label = "Remove from Library", onClick = { onDeleteItem(); onDismiss() }, danger = true)
                    }
                }
            }
        }
    }
}

// Real port of components/cardOptionsMenu.js's own window.confirm()
// gate before deleteItem() ever fires: this app has no real
// blocking confirm dialog to lean on, same shape as CardOptionsMenu
// itself above (a scrim behind a rounded JellioBgElevated surface)
// rather than a platform Dialog window, the same real reason that
// composable's own header gives for rendering at the caller's own
// screen root instead of a separate window a D-pad has to refocus
// into.
@Composable
fun RemoveFromLibraryConfirm(
    item: BaseItemDto,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onCancel,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 320.dp, max = 420.dp)
                .background(JellioBgElevated, RoundedCornerShape(16.dp))
                .padding(24.dp),
        ) {
            Text(text = "Remove from Library", color = JellioText)
            Text(
                text = "Remove \"${item.Name.orEmpty()}\" from your library? This cannot be undone.",
                color = JellioText,
                modifier = Modifier.padding(top = 12.dp, bottom = 20.dp),
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                Surface(
                    onClick = onCancel,
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, contentColor = JellioText, focusedContainerColor = Color.White.copy(alpha = 0.18f), focusedContentColor = JellioText),
                ) {
                    Text(text = "Cancel", modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                }
                Surface(
                    onClick = onConfirm,
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = JellioDanger, contentColor = JellioText),
                    modifier = Modifier.padding(start = 12.dp),
                ) {
                    Text(text = "Remove", modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
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

@Composable
private fun WatchersBadge(
    watchers: List<com.jellio.tv.data.model.CommunityWatcherDto>,
    userImageUrl: ((String, String?) -> String)?,
    modifier: Modifier = Modifier,
) {
    if (watchers.isEmpty()) return

    val displayWatchers = watchers.take(3)
    val overflow = watchers.size - displayWatchers.size

    Row(
        modifier = modifier
            .background(JellioBg.copy(alpha = 0.85f), RoundedCornerShape(999.dp))
            .padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy((-6).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        displayWatchers.forEach { watcher ->
            val avatarUrl = if (!watcher.PrimaryImageTag.isNullOrEmpty() && userImageUrl != null) {
                userImageUrl(watcher.UserId, watcher.PrimaryImageTag)
            } else null

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, JellioBg, CircleShape)
                    .background(JellioSecondary),
            ) {
                if (avatarUrl != null) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = watcher.UserName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    val initial = watcher.UserName.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
                    Text(
                        text = initial,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
        }
        if (overflow > 0) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, JellioBg, CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
            ) {
                Text(
                    text = "+$overflow",
                    color = JellioText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}
