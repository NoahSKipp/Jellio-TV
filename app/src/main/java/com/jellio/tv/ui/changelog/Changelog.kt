package com.jellio.tv.ui.changelog

import com.jellio.tv.ui.common.dismissOnBack
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioBorder
import com.jellio.tv.ui.theme.JellioRarityEpic
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import kotlinx.coroutines.delay

data class ChangelogFeature(
    val icon: ImageVector,
    val title: String,
    val description: String,
)

data class ChangelogRelease(
    val version: String,
    val title: String,
    val subtitle: String,
    val features: List<ChangelogFeature>,
)

// Helper to determine whether a release version is a big release (e.g. 1.4X.0, 0.42.0)
// as opposed to a small bugfix patch release (e.g. 1.4X.1, 0.42.1).
fun isBigRelease(version: String): Boolean {
    val clean = version.trim().removePrefix("v").removePrefix("V")
    val parts = clean.split(".")
    if (parts.size >= 3) {
        val patch = parts[2].toIntOrNull() ?: return false
        val build = if (parts.size > 3) parts[3].toIntOrNull() ?: 0 else 0
        return patch == 0 && build == 0
    }
    return parts.size == 2
}

val CHANGELOG_RELEASES = listOf(
    ChangelogRelease(
        version = "0.85.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.85.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.Sync, "Unified Error Handling with Retry", "Home, library browsing, live TV, person views, and search now provide a dedicated Try Again button with clear messaging when server or network requests fail, avoiding empty or frozen screens."),
            ChangelogFeature(Icons.Filled.Key, "Resilient Session Loss Detection", "Jellio TV now detects when the Jellyfin server terminates an active session, cleanly signing out and prompting re-authentication or direct password entry for saved profiles without infinite loops."),
            ChangelogFeature(Icons.Filled.Info, "Verified Update Installation", "Downloaded application updates are verified for package integrity, valid signatures, and correct package identity before launching the installer, preventing corrupt installs."),
            ChangelogFeature(Icons.Filled.Tv, "In-Page Episode Focus & Progression", "Episode detail pages now automatically open on the episode's own season with focus on the current episode card, and smoothly reset selection when toggling between seasons."),
            ChangelogFeature(Icons.Filled.PlayCircle, "Smart Credits Fallback & Next Up", "When media lacks server-provided credit markers, Jellio TV now automatically offers the Next Up episode prompt near the end of playback, and loads skip segments across all web player sources."),
            ChangelogFeature(Icons.Filled.SettingsRemote, "Quick Hub Remote Hardening", "Remote shortcut recorder validates custom keys, enforces long press on critical remote buttons, properly resets hold states on focus loss, and removes conflicting shortcuts."),
        ),
    ),
    ChangelogRelease(
        version = "0.84.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.84.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.Groups, "Quick Hub Group Watch", "Group Watch has moved into Quick Hub alongside Now Playing and Notifications, offering live party tracking, group creation, invite handling, and instant code entry in one unified drawer."),
            ChangelogFeature(Icons.Filled.SettingsRemote, "Refined Remote Shortcuts & Google TV Support", "Full Google TV remote button capture in the shortcut recorder with smart debouncing and robust long-press handling that prevents the Back button from closing the drawer on release."),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Cycling Active Streams Pill & UI Polish", "The floating Home pill smoothly cycles through multiple concurrent active streams every 4.5 seconds with animated transitions, non-overlapping header tabs, and a refined Quick Hub launcher icon."),
            ChangelogFeature(Icons.Filled.Tune, "Quick Hub Focus Navigation & Pill Styling", "Scrolling or navigating up from within the Quick Hub drawer now directly selects the currently active section badge, paired with centered pill action buttons and TV series show name priority."),
            ChangelogFeature(Icons.Filled.Notifications, "Media Posters & User Profile Pictures", "Notification alerts now display media artwork and announcement posters, and active stream cards show Jellyfin user profile pictures alongside device information."),
        ),
    ),
    ChangelogRelease(
        version = "0.83.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.83.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.GraphicEq, "Live Updating Now Playing Bar", "Playback progress, timestamps, and completion percentage advance live second by second with smooth continuous animations."),
            ChangelogFeature(Icons.Filled.PlayCircle, "Account-Safe Stream Handoff", "Stream handoff is restricted exclusively to your own user account, keeping other users' active streams safe from interruption."),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Glassy Now Playing Pill & Display Toggle", "The floating Now Playing indicator is restyled in Jellio's dark glassy aesthetic, with a new toggle in Settings > Display to hide or show it."),
            ChangelogFeature(Icons.Filled.Notifications, "Notification Timestamps & Deletion", "View relative reception timestamps on every alert, with one-click individual dismissal and a Clear All option."),
        ),
    ),
    ChangelogRelease(
        version = "0.80.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.80.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Unified Quick Hub Drawer", "Access Now Playing, Notifications, and quick controls in a newly designed glassy dark drawer with segmented tabs, live stream counts, and unread badges."),
            ChangelogFeature(Icons.Filled.SettingsRemote, "Remote Button Recorder & Custom Mapping", "Map remote buttons to the Quick Hub using the interactive button recorder, with Long Press or Short Press modes and clear guidance for system-reserved keys."),
            ChangelogFeature(Icons.Filled.Tune, "Streamlined Navigation Rail", "The main navigation rail is simplified and cleaned up, providing quick access while keeping drawer controls within easy reach."),
            ChangelogFeature(Icons.Filled.Settings, "Dedicated Remote Settings", "A dedicated Remote & Shortcuts settings menu allows fine-tuning hold durations, button triggers, and device-specific defaults."),
        ),
    ),
    ChangelogRelease(
        version = "0.71.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.71.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.Tune, "Multi-Edition & Version Recognition", "Automatic recognition and interactive toggle between Color vs Noir / B&W, Theatrical, Extended, and Director's Cuts directly on the detail screen."),
            ChangelogFeature(Icons.Filled.LiveTv, "Dynamic Island Now Playing Pill", "Compact floating status indicator on the home screen showing current playback state, progress, and active stream details."),
            ChangelogFeature(Icons.Filled.Mic, "Firestick Voice Dictation & Banner", "Microphone search automatically opens your on-screen keyboard with Alexa dictation instructions, accompanied by optimized 16:9 TV banners."),
        ),
    ),
    ChangelogRelease(
        version = "0.70.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.70.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Custom Genre Management & Hold to Delete", "Long-press any custom genre row to remove it with modal confirmation, and enable matching library genres directly from search."),
            ChangelogFeature(Icons.Filled.Sync, "Profile Recommendations Sync", "Custom genre rows and genre recommendation selections now sync seamlessly with your Jellio profile across all devices."),
            ChangelogFeature(Icons.Filled.Tv, "1-Click Library Genre Activation", "Search now detects standard library genres like Telenovela and Soap, providing a direct 1-click button to add them to your Home rows."),
        ),
    ),
    ChangelogRelease(
        version = "0.69.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.69.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.Tv, "In-Page Episode Carousel", "The episode detail screen now features an episode carousel directly below the action buttons, allowing you to browse and switch episodes in the season without navigating away."),
            ChangelogFeature(Icons.Filled.Speed, "Hold-to-Seek Remote Control", "Holding Left or Right on your remote D-pad now smoothly accelerates seeking forward or backward with live target timeline feedback and calibrated speed curves."),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Custom Genre Rows & Search", "Search and add custom categories like C-Drama, K-Drama, or Cyberpunk from Recommendations settings directly into your Home rows."),
            ChangelogFeature(Icons.Filled.PlayCircle, "Responsive Action Row & Feedback", "Optimistic feedback for Watchlist, Watched, and Like/Dislike actions with auto-compacting episode action buttons."),
            ChangelogFeature(Icons.Filled.DynamicFeed, "Skipped Episode Progression", "Jumping forward to a later episode in a series automatically marks earlier unfinished episodes as watched, while safely preserving non-linear watch orders for anthology series."),
        ),
    ),
    ChangelogRelease(
        version = "0.68.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.68.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.Tv, "Remote Playback & Smart TV Cast", "Cast and start media directly on your TV from Jellio Web and Jellyfin mobile apps over your local network, with real-time remote playback control including play, pause, seek, stop, volume control, and mute."),
            ChangelogFeature(Icons.Filled.DynamicFeed, "Infinite Genre Rows", "Genre and catalog rows now load continuously as you scroll horizontally to the right, seamlessly paginating new recommendations and virtual catalog items."),
            ChangelogFeature(Icons.Filled.Tune, "Smart Row Deduplication", "Sliding window deduplication ensures titles do not repeat across adjacent genre rows on your home screen while leaving personal rows untouched."),
            ChangelogFeature(Icons.Filled.Palette, "Modern TMDB Rating Badge", "Updated ratings across showcase carousels, cards, title pages, and player overlays to feature the official compact TMDB badge with vibrant linear gradient."),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Curated Google TV Top Picks", "Top pick recommendations for the Google TV launcher are now cleanly capped to a maximum of 6 highlighted items."),
        ),
    ),
)

val CURRENT_CHANGELOG = CHANGELOG_RELEASES.first()
val LATEST_BIG_CHANGELOG = CHANGELOG_RELEASES.firstOrNull { isBigRelease(it.version) } ?: CURRENT_CHANGELOG

object ChangelogPrefs {
    private const val PREFS = "jellio_changelog"
    private const val KEY_LAST_SEEN = "last_seen_version"

    fun shouldShowChangelog(context: Context): Boolean {
        // What's new popup dialog is ONLY triggered by big releases (1.4X.0, where patch is 0)
        val targetRelease = LATEST_BIG_CHANGELOG
        if (!isBigRelease(targetRelease.version)) {
            return false
        }
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastSeen = prefs.getString(KEY_LAST_SEEN, null)
        // If an older release was stored, clear it so the new big release is displayed
        if (lastSeen != null && lastSeen != targetRelease.version) {
            prefs.edit().remove(KEY_LAST_SEEN).apply()
            return true
        }
        return lastSeen == null
    }

    fun markSeen(context: Context, version: String = LATEST_BIG_CHANGELOG.version) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LAST_SEEN, version).apply()
    }
}

// Centered modal popup shown on initial open following a major update,
// or accessed on demand from Settings -> About.
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ChangelogPopup(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    release: ChangelogRelease = CURRENT_CHANGELOG,
    allReleases: List<ChangelogRelease> = CHANGELOG_RELEASES,
) {
    val buttonFocusRequester = remember { FocusRequester() }
    val firstFeatureFocusRequester = remember { FocusRequester() }
    var showPreviousVersions by remember { mutableStateOf(false) }
    var expandedPreviousVersion by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        repeat(10) {
            delay(50)
            if (runCatching { buttonFocusRequester.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    // Its own window: the screen behind claims focus while it loads, which
    // used to pull focus off Continue and behind this popup.
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .dismissOnBack(onDismiss)
            .focusProperties { onExit = { FocusRequester.Cancel } }
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .heightIn(max = 520.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(JellioBgElevated)
                .border(1.dp, JellioBorder, RoundedCornerShape(20.dp))
                .padding(28.dp),
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(JellioRarityEpic.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = JellioRarityEpic,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = release.title,
                        color = JellioText,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                        ),
                    )
                    Text(
                        text = release.subtitle,
                        color = JellioTextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Scrollable feature highlights
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .focusRestorer { firstFeatureFocusRequester }
                    .padding(end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                release.features.forEachIndexed { index, feature ->
                    val isFirst = index == 0
                    Surface(
                        onClick = {},
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.05f),
                            contentColor = JellioText,
                            focusedContainerColor = Color.White.copy(alpha = 0.14f),
                            focusedContentColor = JellioText,
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                        border = ClickableSurfaceDefaults.border(
                            border = Border(
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                shape = RoundedCornerShape(12.dp),
                            ),
                            focusedBorder = Border(
                                border = BorderStroke(2.dp, Color.White),
                                shape = RoundedCornerShape(12.dp),
                            ),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isFirst) Modifier.focusRequester(firstFeatureFocusRequester) else Modifier)
                            .then(if (isFirst) Modifier.focusProperties { up = FocusRequester.Cancel } else Modifier),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = feature.icon,
                                    contentDescription = null,
                                    tint = JellioText,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = feature.title,
                                    color = JellioText,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = feature.description,
                                    color = JellioTextSecondary,
                                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                )
                            }
                        }
                    }
                }

                val previousReleases = allReleases.filter { it.version != release.version }
                if (previousReleases.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        onClick = { showPreviousVersions = !showPreviousVersions },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.05f),
                            contentColor = JellioTextSecondary,
                            focusedContainerColor = Color.White.copy(alpha = 0.15f),
                            focusedContentColor = JellioText,
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                        border = ClickableSurfaceDefaults.border(
                            border = Border(
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                shape = RoundedCornerShape(12.dp),
                            ),
                            focusedBorder = Border(
                                border = BorderStroke(2.dp, Color.White),
                                shape = RoundedCornerShape(12.dp),
                            ),
                        ),
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = com.jellio.tv.data.i18n.Strings.format("changelog_previous_versions", previousReleases.size),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            )
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp).rotate(if (showPreviousVersions) 90f else 0f),
                            )
                        }
                    }

                    if (showPreviousVersions) {
                        previousReleases.forEach { prev ->
                            val isExpanded = expandedPreviousVersion == prev.version
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.03f))
                                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp)),
                            ) {
                                Surface(
                                    onClick = {
                                        expandedPreviousVersion = if (isExpanded) null else prev.version
                                    },
                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = Color.Transparent,
                                        contentColor = JellioText,
                                        focusedContainerColor = Color.White.copy(alpha = 0.12f),
                                        focusedContentColor = JellioText,
                                    ),
                                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
                                    modifier = Modifier.fillMaxWidth().height(40.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = prev.subtitle,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        )
                                        Icon(
                                            imageVector = Icons.Filled.ChevronRight,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp).rotate(if (isExpanded) 90f else 0f),
                                        )
                                    }
                                }

                                if (isExpanded) {
                                    Column(
                                        modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        prev.features.forEach { prevFeature ->
                                            Surface(
                                                onClick = {},
                                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                                colors = ClickableSurfaceDefaults.colors(
                                                    containerColor = Color.Transparent,
                                                    contentColor = JellioText,
                                                    focusedContainerColor = Color.White.copy(alpha = 0.10f),
                                                    focusedContentColor = JellioText,
                                                ),
                                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
                                                border = ClickableSurfaceDefaults.border(
                                                    border = Border.None,
                                                    focusedBorder = Border(
                                                        border = BorderStroke(1.5.dp, Color.White),
                                                        shape = RoundedCornerShape(8.dp),
                                                    ),
                                                ),
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.Top,
                                                ) {
                                                    Icon(
                                                        imageVector = prevFeature.icon,
                                                        contentDescription = null,
                                                        tint = JellioTextSecondary,
                                                        modifier = Modifier.size(16.dp).padding(top = 2.dp),
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = prevFeature.title,
                                                            color = JellioText,
                                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                                        )
                                                        Text(
                                                            text = prevFeature.description,
                                                            color = JellioTextSecondary,
                                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Dismiss Button
            Surface(
                onClick = onDismiss,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = JellioRarityEpic,
                    contentColor = Color.White,
                    focusedContainerColor = Color.White,
                    focusedContentColor = Color.Black,
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .focusRequester(buttonFocusRequester)
                    .focusProperties {
                        up = firstFeatureFocusRequester
                    },
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = com.jellio.tv.data.i18n.Strings.get("changelog_continue"),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
    }
}
