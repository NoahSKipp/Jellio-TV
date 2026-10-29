package com.jellio.tv.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import coil3.request.crossfade
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioDanger
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.scaled
import kotlinx.coroutines.delay
import java.util.Locale

// Only a card the viewer stays on prefetches its streams, never every card
// passed over on the way along a row (mirrors components/card.js).
private const val PREFETCH_DEBOUNCE_MS = 600L

// How long a card has to stay focused before it widens into its banner.
private const val EXPAND_DELAY_MS = 2500L
private const val EXPAND_ANIMATION_MS = 220
private const val TICKS_PER_MINUTE = 10_000_000L * 60L

internal val PosterWidth = 126.dp

private fun imageFor(
    imageUrl: (BaseItemDto, String, Int) -> String,
    id: String,
    tag: String?,
    type: String,
    maxWidth: Int,
): String {
    val synthetic = BaseItemDto(
        Id = id,
        ImageTags = if (!tag.isNullOrEmpty() && type != "Backdrop") mapOf(type to tag) else emptyMap(),
        BackdropImageTags = if (!tag.isNullOrEmpty() && type == "Backdrop") listOf(tag) else emptyList(),
    )
    return imageUrl(synthetic, type, maxWidth)
}

private class CandidateList(
    private val imageUrl: (BaseItemDto, String, Int) -> String,
    private val maxWidth: Int,
) {
    val urls = LinkedHashSet<String>()

    fun tagged(id: String?, tag: String?, type: String) {
        if (id.isNullOrBlank() || tag.isNullOrBlank()) return
        imageFor(imageUrl, id, tag, type, maxWidth).takeIf { it.isNotBlank() }?.let(urls::add)
    }

    fun untagged(id: String?, type: String) {
        if (id.isNullOrBlank()) return
        imageFor(imageUrl, id, null, type, maxWidth).takeIf { it.isNotBlank() }?.let(urls::add)
    }

    fun both(id: String?, tag: String?, type: String) {
        tagged(id, tag, type)
        untagged(id, type)
    }
}

private fun BaseItemDto.isEpisodeLike(): Boolean =
    Type.equals("Episode", ignoreCase = true) || Type.equals("Season", ignoreCase = true)

// Poster art for a 2:3 card (mirrors components/card.js cardImageCandidates).
// seriesFirst puts the show's poster ahead of an episode's own 16:9 still.
internal fun posterImageCandidates(
    item: BaseItemDto,
    imageUrl: (BaseItemDto, String, Int) -> String,
    maxWidth: Int = 400,
    seriesFirst: Boolean = false,
): List<String> {
    val list = CandidateList(imageUrl, maxWidth)
    val seriesPrimaryTag = item.SeriesPrimaryImageTag ?: item.ImageTags?.get("SeriesPrimary")
    if (seriesFirst && item.isEpisodeLike()) list.both(item.SeriesId, seriesPrimaryTag, "Primary")
    list.both(item.Id, item.ImageTags?.get("Primary") ?: item.PrimaryImageTag, "Primary")
    list.both(item.SeriesId, seriesPrimaryTag, "Primary")
    list.tagged(item.ParentThumbItemId, item.ParentThumbImageTag, "Thumb")
    list.both(item.ParentPrimaryImageItemId ?: item.ParentId, item.ParentPrimaryImageTag, "Primary")
    list.both(item.AlbumId, item.AlbumPrimaryImageTag, "Primary")
    list.tagged(item.Id, item.ImageTags?.get("Thumb") ?: item.ThumbImageTag, "Thumb")
    list.tagged(item.Id, item.BackdropImageTags?.firstOrNull(), "Backdrop")
    list.tagged(item.ParentBackdropItemId, item.ParentBackdropImageTags?.firstOrNull(), "Backdrop")
    list.untagged(item.Id, "Thumb")
    list.untagged(item.Id, "Backdrop")
    return list.urls.toList()
}

// Wide art for the banner card and the Home showcase. An episode carries no
// backdrop of its own, so its show's comes first unless episodeStill asks for
// the episode's own picture (Continue Watching, Up Next).
internal fun posterBackdropCandidates(
    item: BaseItemDto,
    imageUrl: (BaseItemDto, String, Int) -> String,
    maxWidth: Int = 800,
    episodeStill: Boolean = false,
    series: BaseItemDto? = null,
): List<String> {
    val list = CandidateList(imageUrl, maxWidth)
    val ownPrimary = item.ImageTags?.get("Primary") ?: item.PrimaryImageTag
    if (episodeStill && item.isEpisodeLike()) list.tagged(item.Id, ownPrimary, "Primary")
    // An episode's or season's own backdrop is often a small episode still
    // (Gelato seasons), so the show's artwork goes first for those.
    val ownBackdrop = item.BackdropImageTags?.firstOrNull() ?: item.ImageTags?.get("Backdrop")
    if (!item.isEpisodeLike()) list.tagged(item.Id, ownBackdrop, "Backdrop")
    if (series != null) list.tagged(series.Id, series.BackdropImageTags?.firstOrNull(), "Backdrop")
    list.tagged(item.ParentBackdropItemId, item.ParentBackdropImageTags?.firstOrNull(), "Backdrop")
    list.untagged(item.SeriesId, "Backdrop")
    if (item.isEpisodeLike()) list.tagged(item.Id, ownBackdrop, "Backdrop")
    list.tagged(item.ParentThumbItemId, item.ParentThumbImageTag, "Thumb")
    list.tagged(item.Id, item.ImageTags?.get("Thumb") ?: item.ThumbImageTag, "Thumb")
    if (item.isEpisodeLike()) list.tagged(item.Id, ownPrimary, "Primary")
    list.untagged(item.Id, "Backdrop")
    list.tagged(item.Id, ownPrimary, "Primary")
    return list.urls.toList()
}

// Clear logo: the item's own, else its show's.
internal fun posterLogoCandidates(
    item: BaseItemDto,
    imageUrl: (BaseItemDto, String, Int) -> String,
    maxWidth: Int = 400,
    series: BaseItemDto? = null,
): List<String> {
    val list = CandidateList(imageUrl, maxWidth)
    list.tagged(item.Id, item.ImageTags?.get("Logo"), "Logo")
    if (series != null) list.tagged(series.Id, series.ImageTags?.get("Logo"), "Logo")
    list.tagged(item.ParentLogoItemId, item.ParentLogoImageTag, "Logo")
    list.untagged(item.SeriesId, "Logo")
    if (!item.isEpisodeLike()) list.untagged(item.Id, "Logo")
    return list.urls.toList()
}

internal fun episodeLabel(item: BaseItemDto): String {
    if (!item.Type.equals("Episode", ignoreCase = true)) return ""
    val season = item.ParentIndexNumber ?: return ""
    val episode = item.IndexNumber ?: return ""
    return "S$season E$episode"
}

private fun remainingLabel(item: BaseItemDto): String {
    val runTicks = item.RunTimeTicks ?: return ""
    val posTicks = item.UserData?.PlaybackPositionTicks ?: return ""
    if (runTicks <= 0 || posTicks <= 0 || posTicks >= runTicks) return ""
    val minutes = Math.round((runTicks - posTicks).toDouble() / TICKS_PER_MINUTE)
    return if (minutes > 0) "${minutes}m left" else ""
}

// A 2:3 poster that widens into a 16:9 banner (backdrop plus logo) once it has
// been focused for EXPAND_DELAY_MS, and narrows again as soon as focus leaves.
// progress is the Continue Watching / Up Next look: the show's poster, then
// the episode's own still when widened, with the time left and a progress bar.
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    progress: Boolean = false,
    isRemoving: Boolean = false,
    onShatterFinished: () -> Unit = {},
    // A label in the card's top-left corner, such as a release date.
    badge: String? = null,
) {
    val baseWidth = PosterWidth.scaled()
    val baseHeight = (PosterWidth * 1.5f).scaled()
    val expandedWidth = baseHeight * (16f / 9f)
    val prefetch = LocalGelatoPrefetch.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var isFocused by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }
    val bringIntoView = remember { BringIntoViewRequester() }

    LaunchedEffect(isFocused, canExpand) {
        isExpanded = false
        if (!isFocused || !canExpand) return@LaunchedEffect
        delay(EXPAND_DELAY_MS)
        isExpanded = true
        // A card near the end of a row grows past the right edge; once it
        // has finished widening, scroll the row so all of it shows.
        delay(EXPAND_ANIMATION_MS + 40L)
        runCatching { bringIntoView.bringIntoView() }
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val baseWidthPx = with(density) { baseWidth.roundToPx() }.coerceAtLeast(1)
    val baseHeightPx = with(density) { baseHeight.roundToPx() }.coerceAtLeast(1)
    val expandedWidthPx = with(density) { expandedWidth.roundToPx() }.coerceAtLeast(1)
    val logoHeightPx = with(density) { 40.dp.scaled().roundToPx() }.coerceAtLeast(1)

    val candidates = remember(item, imageUrl, progress) { posterImageCandidates(item, imageUrl, 400, seriesFirst = progress) }
    var candidateIndex by remember(item.Id, candidates) { androidx.compose.runtime.mutableIntStateOf(0) }
    val activeUrl = candidates.getOrNull(candidateIndex)

    // Banner art is only worked out for the focused card, not every card in view.
    val wantsBanner = isFocused && canExpand
    val backdropCandidates = remember(item, imageUrl, progress, wantsBanner) {
        if (wantsBanner) posterBackdropCandidates(item, imageUrl, 800, episodeStill = progress) else emptyList()
    }
    var backdropIndex by remember(item.Id, backdropCandidates) { androidx.compose.runtime.mutableIntStateOf(0) }
    val activeBackdropUrl = backdropCandidates.getOrNull(backdropIndex) ?: activeUrl

    val logoCandidates = remember(item, imageUrl, wantsBanner) {
        if (wantsBanner) posterLogoCandidates(item, imageUrl, 300) else emptyList()
    }
    var logoIndex by remember(item.Id, logoCandidates) { androidx.compose.runtime.mutableIntStateOf(0) }
    val activeLogoUrl = logoCandidates.getOrNull(logoIndex)

    // Resting on a card warms its banner and logo with the exact requests the
    // widened card makes, so they are in memory by the time it widens.
    LaunchedEffect(isFocused, item.Id) {
        if (!isFocused) return@LaunchedEffect
        delay(PREFETCH_DEBOUNCE_MS)
        if (com.jellio.tv.ui.perf.ScrollActivity.busy) return@LaunchedEffect
        prefetch(item)
        if (!canExpand) return@LaunchedEffect
        val loader = coil3.SingletonImageLoader.get(context)
        backdropCandidates.firstOrNull()?.let { loader.enqueue(sizedImageRequest(context, it, expandedWidthPx, baseHeightPx)) }
        logoCandidates.firstOrNull()?.let { loader.enqueue(sizedImageRequest(context, it, expandedWidthPx, logoHeightPx)) }
    }

    val animatedWidth by animateDpAsState(
        targetValue = if (isExpanded) expandedWidth else baseWidth,
        animationSpec = tween(durationMillis = EXPAND_ANIMATION_MS, easing = FastOutSlowInEasing),
        label = "posterCardWidth",
    )

    val episode = episodeLabel(item)
    val remaining = if (progress) remainingLabel(item) else ""
    val percentage = item.UserData?.PlayedPercentage?.takeIf { it > 0 && item.UserData?.Played != true }

    if (isRemoving) {
        Box(modifier = modifier.width(baseWidth).height(baseHeight)) {
            val shatterUrl = activeUrl ?: candidates.firstOrNull()
            if (shatterUrl != null) {
                CardShatterOverlay(imageUrl = shatterUrl, cardWidth = baseWidth, cardHeight = baseHeight, onFinished = onShatterFinished)
            } else {
                LaunchedEffect(item.Id) { onShatterFinished() }
            }
        }
        return
    }

    Surface(
        onClick = onClick,
        onLongClick = onOptionsClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = JellioBgElevated,
            contentColor = JellioText,
            focusedContainerColor = JellioBgElevated,
            focusedContentColor = JellioText,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(12.dp),
            ),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        modifier = modifier
            .bringIntoViewRequester(bringIntoView)
            .width(animatedWidth)
            .height(baseHeight)
            .onFocusChanged { state ->
                if (state.isFocused && !isFocused) onFocus?.invoke(item)
                isFocused = state.isFocused
            },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isExpanded) {
                if (activeBackdropUrl != null) {
                    AsyncImage(
                        model = remember(activeBackdropUrl) { sizedImageRequest(context, activeBackdropUrl, expandedWidthPx, baseHeightPx) },
                        contentDescription = item.Name,
                        contentScale = ContentScale.Crop,
                        onError = { if (backdropIndex < backdropCandidates.size) backdropIndex += 1 },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.7f)
                        .align(Alignment.BottomStart)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f)))),
                )
                if (remaining.isNotEmpty()) {
                    CardChip(text = remaining, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, end = 12.dp, bottom = if (percentage != null) 12.dp else 10.dp)
                        .fillMaxWidth(0.75f),
                ) {
                    if (activeLogoUrl != null) {
                        AsyncImage(
                            model = remember(activeLogoUrl) { sizedImageRequest(context, activeLogoUrl, expandedWidthPx, logoHeightPx) },
                            contentDescription = item.SeriesName ?: item.Name,
                            onError = { logoIndex += 1 },
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.BottomStart,
                            modifier = Modifier.height(40.dp.scaled()).fillMaxWidth(),
                        )
                    } else {
                        com.jellio.tv.ui.common.FocusMarqueeText(
                            text = if (item.isEpisodeLike()) item.SeriesName ?: item.Name.orEmpty() else item.Name.orEmpty(),
                            focused = isFocused,
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (progress && episode.isNotEmpty()) {
                        com.jellio.tv.ui.common.FocusMarqueeText(
                            text = listOfNotNull(episode, item.Name?.takeIf { it.isNotBlank() }?.let { com.jellio.tv.ui.i18n.episodeDisplayName(it, item.IndexNumber) }).joinToString(" \u00B7 "),
                            focused = isFocused,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                        )
                    }
                }
            } else {
                if (activeUrl != null) {
                    AsyncImage(
                        model = remember(activeUrl) { sizedImageRequest(context, activeUrl, baseWidthPx, baseHeightPx, crossfade = true) },
                        contentDescription = item.Name,
                        contentScale = ContentScale.Crop,
                        onError = { if (candidateIndex < candidates.size) candidateIndex += 1 },
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
                            text = item.SeriesName ?: item.Name.orEmpty(),
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
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                    )
                }
                if (!progress) {
                    item.CommunityRating.let { rating ->
                        RatingChip(
                            rating = rating,
                            modifier = Modifier
                                .align(if (!watchers.isNullOrEmpty() && badge == null) Alignment.TopStart else Alignment.TopEnd)
                                .padding(6.dp),
                        )
                    }
                }
                if (badge != null) {
                    Text(
                        text = badge,
                        color = com.jellio.tv.ui.theme.JellioRarityLegendary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(JellioBg.copy(alpha = 0.85f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                if (progress && episode.isNotEmpty()) {
                    CardChip(
                        text = episode,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 6.dp, bottom = if (percentage != null) 10.dp else 6.dp),
                    )
                }
                if (item.UserData?.Played == true) {
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
                }
            }
            if (percentage != null) {
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

// Card art at the card's own pixel size, keyed by that size so a prefetch and
// the card read the same memory cache entry (same approach as NuvioTV's
// ContentCard, GPL-3.0).
internal fun sizedImageRequest(
    context: android.content.Context,
    url: String,
    widthPx: Int,
    heightPx: Int,
    crossfade: Boolean = false,
): coil3.request.ImageRequest =
    coil3.request.ImageRequest.Builder(context)
        .data(url)
        .memoryCacheKey("${url}_${widthPx}x$heightPx")
        .size(widthPx, heightPx)
        .apply { if (crossfade) crossfade(150) }
        .build()

// A title's TMDB rating on a card: the TMDB badge and the score in one chip.
@Composable
internal fun RatingChip(
    rating: Double?,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 12.sp,
    badgeFontSize: androidx.compose.ui.unit.TextUnit = 8.sp,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(999.dp))
            .padding(start = 5.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
    ) {
        TmdbBadge(fontSize = badgeFontSize)
        Text(
            // No TMDB rating yet: a dash, so every card shows the same chip.
            text = rating?.takeIf { it > 0.0 }?.let { String.format(Locale.US, "%.1f", it) } ?: "–",
            color = JellioText,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
internal fun CardChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = JellioText,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
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
    onRemoveShowFromRow: (() -> Unit)? = null,
    onToggleWatchlist: (() -> Unit)? = null,
    onToggleWatched: (() -> Unit)? = null,
    comingSoon: Boolean = false,
    onRemoveFromComingSoon: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val isWatchlisted = item.UserData?.IsFavorite == true
    val isPlayed = item.UserData?.Played == true
    val actions = buildList {
        when {
            comingSoon -> {
                onGoToDetails?.let { add(MenuAction(com.jellio.tv.ui.i18n.Strings.get("menu_go_to_details"), it)) }
                onRemoveFromComingSoon?.let { add(MenuAction(com.jellio.tv.ui.i18n.Strings.get("menu_remove_upcoming"), it, danger = true)) }
            }
            continueWatching -> {
                onGoToDetails?.let { add(MenuAction(com.jellio.tv.ui.i18n.Strings.get("menu_go_to_details"), it)) }
                onPlayManually?.let { add(MenuAction(com.jellio.tv.ui.i18n.Strings.get("menu_play_manually"), it)) }
                onStartOver?.let { add(MenuAction(com.jellio.tv.ui.i18n.Strings.get("menu_start_beginning"), it)) }
                onRemoveFromRow?.let { add(MenuAction(com.jellio.tv.ui.i18n.Strings.get("menu_remove_continue"), it, danger = true)) }
            }
            upNext -> {
                onGoToDetails?.let { add(MenuAction("Go to details", it)) }
                // Jellyfin can't hide one episode from Next Up; marking it
                // played is what drops it off the row.
                onRemoveFromRow?.let { add(MenuAction(com.jellio.tv.ui.i18n.Strings.get("menu_remove_up_next"), it, danger = true)) }
                // Hides the whole show server side, so its next episode
                // doesn't take its place.
                onRemoveShowFromRow?.let { add(MenuAction(com.jellio.tv.ui.i18n.Strings.get("menu_remove_show_up_next"), it, danger = true)) }
            }
            else -> {
                onToggleWatchlist?.let { add(MenuAction(if (isWatchlisted) com.jellio.tv.ui.i18n.Strings.get("menu_remove_watchlist") else com.jellio.tv.ui.i18n.Strings.get("menu_add_watchlist"), it)) }
                onToggleWatched?.let { add(MenuAction(if (isPlayed) com.jellio.tv.ui.i18n.Strings.get("detail_mark_unwatched") else com.jellio.tv.ui.i18n.Strings.get("detail_mark_watched"), it)) }
            }
        }
    }
    CardDialog(onDismiss = onDismiss) { firstFocus ->
        Column(
            modifier = Modifier
                .widthIn(min = 320.dp, max = 460.dp)
                .background(JellioBgElevated, RoundedCornerShape(16.dp))
                .padding(vertical = 12.dp),
        ) {
            Text(
                text = item.SeriesName?.takeIf { continueWatching || upNext } ?: item.Name.orEmpty(),
                color = JellioText,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            )
            actions.forEachIndexed { index, action ->
                CardOptionsMenuRow(
                    label = action.label,
                    danger = action.danger,
                    onClick = {
                        action.onClick()
                        onDismiss()
                    },
                    modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                )
            }
        }
    }
}

private data class MenuAction(val label: String, val onClick: () -> Unit, val danger: Boolean = false)

// A menu in its own window: always on top of the screen (it used to be laid
// out inside whatever screen opened it, stretched or underneath), holding
// focus until it closes. The key up that ends the long press which opened it
// is swallowed, so it can't pick the first option by itself.
@Composable
private fun CardDialog(onDismiss: () -> Unit, content: @Composable (FocusRequester) -> Unit) {
    val firstFocus = remember { FocusRequester() }
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        repeat(10) {
            if (runCatching { firstFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }
    LaunchedEffect(Unit) {
        delay(700)
        armed = true
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .onPreviewKeyEvent { event ->
                    // First Back closes it (left alone, the first press only moves focus).
                    if (event.key == Key.Back) {
                        if (event.type == KeyEventType.KeyUp) onDismiss()
                        return@onPreviewKeyEvent true
                    }
                    val select = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                    if (!armed && select) {
                        if (event.type == KeyEventType.KeyUp) armed = true
                        true
                    } else {
                        if (event.type == KeyEventType.KeyDown) armed = true
                        false
                    }
                }
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss,
                ),
        ) {
            content(firstFocus)
        }
    }
}

@Composable
private fun CardOptionsMenuRow(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, danger: Boolean = false) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = if (danger) JellioDanger else JellioText,
            focusedContainerColor = Color.White,
            focusedContentColor = if (danger) Color(0xFFC62840) else JellioBg,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
    ) {
        Text(text = label, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
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
        modifier = modifier,
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
                    .size(22.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, Color(0xFF141414), CircleShape)
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
                            fontSize = 10.sp,
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
                    .size(22.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, Color(0xFF141414), CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
            ) {
                Text(
                    text = "+$overflow",
                    color = JellioText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}
