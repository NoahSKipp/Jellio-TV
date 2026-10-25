package com.jellio.tv.ui.changelog

import com.jellio.tv.ui.common.dismissOnBack
import android.content.Context
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
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
        version = "0.55.3",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.55.3",
        features = listOf(
            ChangelogFeature(Icons.Filled.Tv, "Rows Scroll Cleanly", "Scrolling now brings each row fully to the top of the list, so the row above no longer peeks up over the header text."),
        ),
    ),
    ChangelogRelease(
        version = "0.55.1",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.55.1",
        features = listOf(
            ChangelogFeature(Icons.Filled.Tv, "Header Text Stays Clear", "A long title or logo now shortens the description instead of pushing it down behind the rows."),
        ),
    ),
    ChangelogRelease(
        version = "0.55.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.55.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.Tv, "Season by Season", "Picking a season on a show's page now shows that season's own description, cast and backdrop where it has them."),
        ),
    ),
    ChangelogRelease(
        version = "0.54.8",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.54.8",
        features = listOf(
            ChangelogFeature(Icons.Filled.Tv, "Rows Stay in Their Lane", "Rows no longer slide over the description at the top while you scroll, and Coming Soon cards are the same size as the other rows so nothing gets cut off."),
            ChangelogFeature(Icons.Filled.Tv, "Feed Reads Properly", "Feed entries show the real season and episode numbers again, and moving into the list starts at the newest entry."),
            ChangelogFeature(Icons.Filled.Tv, "Sharper Show Headers", "Episodes and seasons now use the show's own artwork for the header instead of a small episode still."),
        ),
    ),
    ChangelogRelease(
        version = "0.54.7",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.54.7",
        features = listOf(
            ChangelogFeature(Icons.Filled.Tv, "Cleaner Header Layout", "Rows now take the lower half of the screen with more of the next row in view, and the title and description sit just above them instead of crowding the cards."),
        ),
    ),
    ChangelogRelease(
        version = "0.54.6",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.54.6",
        features = listOf(
            ChangelogFeature(Icons.Filled.Tv, "Header Looks Right Again", "The backdrop at the top of Home, libraries and Studio Hubs is full size again, and the dark fade no longer covers titles or cuts off rows as they scroll underneath."),
        ),
    ),
    ChangelogRelease(
        version = "0.54.5",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.54.5",
        features = listOf(
            ChangelogFeature(Icons.Filled.Info, "Everything Loads Again", "Version 0.54.4 couldn't load anything from your server, leaving Home empty and your libraries missing from the menu. That's fixed."),
        ),
    ),
    ChangelogRelease(
        version = "0.54.4",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.54.4",
        features = listOf(
            ChangelogFeature(Icons.Filled.Speed, "Leaner and Faster", "Jellio TV is now built as a smaller, optimised app, so it installs quicker and runs a little lighter on your TV."),
            ChangelogFeature(Icons.Filled.Tv, "Smoother Navigation", "Moving around Settings and between screens lands where you expect, focus highlights are clearer, and the top of the screen no longer cuts off the backdrop."),
            ChangelogFeature(Icons.Filled.Info, "Steadier Connection", "When your server is briefly busy, show pages, seasons and episodes try again on their own instead of failing, and Group Watch checks in less often while you're not in a group."),
            ChangelogFeature(Icons.Filled.Settings, "Better German", "The German translation now uses the informal Du everywhere, and genres, seasons and episodes show in your language."),
        ),
    ),
    ChangelogRelease(
        version = "0.54.3",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.54.3",
        features = listOf(
            ChangelogFeature(Icons.Filled.Info, "Complete Multi-Language Localization", "Full end-to-end translation parity across English, German, Spanish, French, Italian, Polish, and Portuguese for all screens, settings, overlays, and playback controls."),
            ChangelogFeature(Icons.Filled.Tune, "Watchers Avatar Styling", "Removed thick black background pill padding from poster card watchers, displaying clean overlapping circular avatars with slim borders."),
            ChangelogFeature(Icons.Filled.Tv, "Showcase Backdrop Seam Fix", "Expanded top hero showcase dark overlay past the bottom edge to cleanly conceal backdrop art seams across Home, Movies, Shows, Anime, and Studio Hubs."),
        ),
    ),
    ChangelogRelease(
        version = "0.54.2",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.54.2",
        features = listOf(
            ChangelogFeature(Icons.Filled.Tune, "Intuitive Onboarding Focus", "Initial focus on setup wizard pages now lands directly on the logical default selection (such as English on the language step or Center on overscan calibration) instead of defaulting to Next."),
            ChangelogFeature(Icons.Filled.PlayCircle, "Smooth Completion Flow", "Focus reliably lands on the Start Exploring action on the final setup summary screen."),
        ),
    ),
    ChangelogRelease(
        version = "0.54.1",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.54.1",
        features = listOf(
            ChangelogFeature(Icons.Filled.Tune, "Setup Wizard Layout Fixed", "Optimized onboarding modal dimensions and spacing for TV screens so Next and Finish buttons are always clearly visible and never cut off."),
            ChangelogFeature(Icons.Filled.Palette, "Language Grid Alignment", "Portuguese and single odd-numbered languages now align in a balanced column instead of stretching across the screen."),
            ChangelogFeature(Icons.Filled.Info, "Localization Fixes", "Corrected step counter formatting and TV-specific text for French, Italian, and Portuguese."),
        ),
    ),
    ChangelogRelease(
        version = "0.54.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.54.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "TV Setup Wizard", "A guided onboarding flow starting from Jellyfin server connection with automatic protocol autofill, user sign-in, language selection, and screen margin calibration. Access it again anytime from Settings > About."),
            ChangelogFeature(Icons.Filled.Sync, "Sign In with Quick Connect", "Sign into your Jellyfin account seamlessly using a 6-digit Quick Connect code from your phone, tablet, or browser without typing your password."),
            ChangelogFeature(Icons.Filled.Tune, "Display Safe Area Inset", "Interactive 4-corner bracket calibration tool in Settings > Display to easily adjust screen margins from 0% to 8% and fix TV overscan."),
            ChangelogFeature(Icons.Filled.Palette, "Display Languages", "Full TV interface localization in English, Deutsch, Español, Français, Italiano, Polski, and Português. Automatically requests media overviews in your chosen language."),
            ChangelogFeature(Icons.Filled.PlayCircle, "Smart Account Sync", "Audio and subtitle preferences configured on Jellio Web are automatically recognized on your TV."),
        ),
    ),
    ChangelogRelease(
        version = "0.53.3",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.53.3",
        features = listOf(
            ChangelogFeature(Icons.Filled.Info, "Correct Version in Jellyfin", "Jellyfin's Devices and Dashboard now show the Jellio TV version you actually have installed, instead of 0.1.0."),
        ),
    ),
    ChangelogRelease(
        version = "0.53.2",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.53.2",
        features = listOf(
            ChangelogFeature(Icons.Filled.Info, "Skip Prompts at the Right Time", "Bad credits timestamps could show Skip Credits and Up Next right as an episode started. Credits now only count in the second half of an episode."),
        ),
    ),
    ChangelogRelease(
        version = "0.53.1",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.53.1",
        features = listOf(
            ChangelogFeature(Icons.Filled.Settings, "Tidier Settings", "Category names no longer wrap onto a second line, and the test tools for screen overlays, image loading and the Google TV row are gone."),
            ChangelogFeature(Icons.Filled.Tv, "Easier Settings Navigation", "Press OK on a category to jump into its settings, and Back to return to the category list. Back from the list leaves Settings."),
            ChangelogFeature(Icons.Filled.Speed, "Faster Studio Hubs", "Studio Hubs, catalog rows on Home and the Anime library load much faster, and each hub row shows up as soon as it's ready instead of waiting for the rows above it."),
        ),
    ),
    ChangelogRelease(
        version = "0.53.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.53.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.Tv, "Studio Hubs Redesigned", "Studio Hubs now look like Home: the title you're on fills the top of the screen with its backdrop and details, tinted in the service's colour, with the service's logo by the filters."),
            ChangelogFeature(Icons.Filled.Speed, "Better Rows in Studio Hubs", "Top 10 and trending rows come first, then what's new, then rows for the hub's biggest genres. Rows that mostly repeat another one are left out."),
            ChangelogFeature(Icons.Filled.Info, "More Studio Hub Logos", "AMC+, Shudder, Discovery+ and Sky Go now show their logos instead of their name. Needs Jellio 1.85.0 on your server."),
            ChangelogFeature(Icons.Filled.Settings, "Anime Stays in Anime", "Anime is now recognised much more reliably, so it no longer turns up in Home, Movies and Series. Needs Jellio 1.85.0 and the latest Gelato on your server."),
        ),
    ),
    ChangelogRelease(
        version = "0.52.5",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.52.5",
        features = listOf(
            ChangelogFeature(Icons.Filled.Speed, "Instant Studio Hubs", "Studio Hubs open straight away with what they showed last time and refresh in the background, like Home and your libraries already do."),
            ChangelogFeature(Icons.Filled.Tv, "Quicker Home Refresh", "Recommendation and genre rows reuse what they already loaded for half an hour, so Home refreshes faster and asks your server for less."),
        ),
    ),
    ChangelogRelease(
        version = "0.52.4",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.52.4",
        features = listOf(
            ChangelogFeature(Icons.Filled.Speed, "Faster Episode Start", "Episodes you resume now start right where you left off, instead of loading from the beginning first and then jumping ahead. Playback also gets going a little sooner."),
            ChangelogFeature(Icons.Filled.Info, "Runtime Shown Right Away", "The player shows the episode's full length and where it starts as soon as it opens, instead of 0:00 / 0:00 until the video has loaded."),
        ),
    ),
    ChangelogRelease(
        version = "0.52.3",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.52.3",
        features = listOf(
            ChangelogFeature(Icons.Filled.Info, "Update Notes Ready to Close", "The notes shown after an update now open with Continue already selected, so one press closes them."),
        ),
    ),
    ChangelogRelease(
        version = "0.52.2",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.52.2",
        features = listOf(
            ChangelogFeature(Icons.Filled.Info, "Search Crash Fixed", "Typing a search letter by letter no longer closes the app while results are coming in."),
            ChangelogFeature(Icons.Filled.Speed, "Faster Libraries and Studio Hubs", "Shows, Anime and Studio Hubs show their first rows straight away and fill in the rest as it arrives, instead of waiting for everything. A slow request no longer holds up the whole page."),
            ChangelogFeature(Icons.Filled.Tv, "Fuller Rows", "Rows on Home that skip titles already shown above now dig deeper, so they're no longer left with just a few cards. Search posters also load faster."),
            ChangelogFeature(Icons.Filled.Settings, "Language Settings Stick", "Choosing a default audio or subtitle language now stays shown right away instead of jumping back to the old one."),
        ),
    ),
    ChangelogRelease(
        version = "0.52.1",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.52.1",
        features = listOf(
            ChangelogFeature(Icons.Filled.PlayCircle, "Tidier Card Menus", "Remove from Continue Watching and Remove from Up Next are back to only showing on the Continue Watching and Up Next rows. Other cards just offer Watchlist and Watched."),
        ),
    ),
    ChangelogRelease(
        version = "0.52.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.52.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.Tune, "One Press of Back", "Menus and pop-ups like the profile switcher, card menu, library picker and filters now close with a single press of Back."),
            ChangelogFeature(Icons.Filled.PlayCircle, "Remove from Continue Watching and Up Next", "Hold select on any card to take it off Continue Watching or Up Next. Removing something from Continue Watching no longer marks it as watched, it just forgets where you stopped. Remove from Library is no longer offered on the TV."),
        ),
    ),
    ChangelogRelease(
        version = "0.51.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.51.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.Bedtime, "Rows for the Time of Day", "Movies, Shows and Anime now open on a row picked for the time of day, with a different take each day: easy family movies in the morning, thrillers in the evening, after-dark horror late at night. Anime gets its own, like slice of life with breakfast, battle shōnen in the afternoon and isekai after midnight."),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Card Menu Fixed", "Holding select on a card now opens a proper menu on top of everything, ready to use with the remote, instead of a stretched menu you couldn't select anything in. Letting go of the button no longer picks the first option by accident."),
        ),
    ),
    ChangelogRelease(
        version = "0.50.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.50.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.Tv, "Libraries Look Like Home", "Movies, Shows and Anime now have the same big backdrop and details at the top as Home, following whatever you're on, with the rows scrolling below. The old carousels are gone."),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Featured Rows", "What the carousels used to show is now the first row: Featured picks in Movies, a time of day pick in Shows and Trending on AniList in Anime."),
        ),
    ),
    ChangelogRelease(
        version = "0.49.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.49.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.Group, "A New Feed", "The activity feed is built for the TV now: a list grouped by day next to a big spotlight of whatever you're on, with its artwork, who watched it and when. Filters show how many entries each type has."),
            ChangelogFeature(Icons.Filled.Settings, "Easier Account Switching", "Switching to a saved account sticks now instead of asking to sign in again, and switching to an account with a password opens the sign in form with that account's name already filled in."),
            ChangelogFeature(Icons.Filled.Info, "Simpler Server Address", "Type your server address without http:// or https://. Jellio tries both, and if neither answers it tells you exactly which addresses it tried."),
            ChangelogFeature(Icons.Filled.Tv, "Launcher Opens the Title", "Picking something from Jellio's rows on the Google TV home now opens its episode or movie page right away, instead of a long wait before playback started on its own."),
            ChangelogFeature(Icons.Filled.PlayCircle, "Cleaner Player Info", "The show and episode now sit just above the seek bar instead of the top corner. The \"You're watching\" screen appears after a few seconds paused, in place of the controls rather than on top of them, and any button brings the controls back."),
        ),
    ),
    ChangelogRelease(
        version = "0.48.1",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.48.1",
        features = listOf(
            ChangelogFeature(Icons.Filled.Speed, "Much Smoother Scrolling", "Jellio TV now ships as an optimised build instead of a debug one, images load at the size they're shown, and moving between cards no longer redraws the whole Home screen. The seasonal scene also pauses while a row scrolls."),
            ChangelogFeature(Icons.Filled.Tv, "Instant Descriptions and Banners", "Descriptions on Home appear straight away, and a card's banner and logo load while you rest on it, so they're ready the moment it widens."),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Fixes", "Genre rows no longer repeat the same title over and over, and library banners no longer show under the sidebar."),
        ),
    ),
    ChangelogRelease(
        version = "0.48.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.48.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.Tv, "A Better Home Showcase", "The top of Home now shows the title you're on with its backdrop, logo, details and description. Episodes use their show's artwork and logo, ratings carry a TMDB badge, and shows still airing get an Ongoing badge."),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Posters That Grow", "Continue Watching and Up Next now use the same slim posters as every other row. Rest on a card for a moment and it widens into a banner, showing the episode's own picture, how much is left and your progress."),
            ChangelogFeature(Icons.Filled.Speed, "Rows That Scroll Ahead", "The card you're on stays at the left of its row, so what's coming next slides in from the right, all the way to View All. This works on Home, in every library, on title pages and on cast pages."),
            ChangelogFeature(Icons.Filled.Palette, "One Dark Background", "Every screen and the sidebar now share the same dark background, and Home opens faster because it no longer loads the old banner carousel."),
        ),
    ),
    ChangelogRelease(
        version = "0.47.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.47.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.Tv, "Permanent Showcase & Backdrop Art", "Home features a permanent top showcase displaying the highlighted selection's backdrop art with smooth horizontal and vertical gradients, clear logo, metadata, ongoing status, and overview synopsis."),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Compact Cards & Focus Expansion", "Compact poster cards display more items per row and smoothly expand into a 16:9 banner with backdrop art, title logo, and high-contrast white focus ring when lingered on."),
            ChangelogFeature(Icons.Filled.Speed, "Smooth Row Streaming", "Horizontal rows stream upcoming items continuously across the screen up to 20 items, while dedicated TV scrolling keeps focused rows neatly aligned below the fixed showcase."),
            ChangelogFeature(Icons.Filled.Tune, "Dark Sidebar Navigation", "Sidebar navigation seamlessly blends into the dark screen background with refined elevation shadows and clean borders."),
        ),
    ),
    ChangelogRelease(
        version = "0.46.9",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.46.9",
        features = listOf(
            ChangelogFeature(Icons.Filled.PlayCircle, "Direct Play Remux Fallback", "Restored automatic server transcode and remux fallback when direct play fails before the first frame, avoiding premature stream failures."),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Stream Poisoning Recovery", "Explicitly chosen streams are automatically unmarked from bad stream health records so they can be replayed immediately, and initial startup errors no longer trigger cascading failovers."),
        ),
    ),
    ChangelogRelease(
        version = "0.46.8",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.46.8",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Scraper Sync Stream Deduplication", "Repeated sync clones of the same scraper release are now cleanly deduplicated, keeping the newest synced proxy URL while preserving probed video and audio details."),
            ChangelogFeature(Icons.Filled.PlayCircle, "Seamless Stream Failover & Remuxing", "Probed Matroska streams now consistently use server-side stream-copy remuxing to avoid static direct-play stalls, and failed streams advance immediately to the next candidate."),
        ),
    ),
    ChangelogRelease(
        version = "0.46.7",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.46.7",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Accurate Stream Health Isolation", "Failed streams are now keyed strictly by their unique source ID rather than release descriptions, ensuring one failing stream never poisons other streams on the title, and purging legacy poisoned records."),
            ChangelogFeature(Icons.Filled.PlayCircle, "Failover & Direct Play Stability", "Preserved the failover notice until playback genuinely starts moving, avoided premature seek requests before player preparation, and displayed transcode retry notices."),
        ),
    ),

    ChangelogRelease(
        version = "0.46.6",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.46.6",
        features = listOf(
            ChangelogFeature(Icons.Filled.PlayCircle, "Native Embedded Subtitle Playback", "Direct-play streams now use the container's native embedded subtitle tracks instead of sideloading all WebVTT tracks at startup, preventing Media3 MergingMediaSource crashes and stream failover loops on previously played episodes."),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Probed Container Direct Play", "Normalized ffprobe container strings (matroska and mov) and cleared falsely flagged bad streams so previously played episodes direct-play cleanly."),
        ),
    ),
    ChangelogRelease(
        version = "0.46.5",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.46.5",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Accurate Bad Stream Tracking", "Failed streams are now keyed by their unique source ID and release filename rather than the shared quality tier header, preventing a single failed stream from flagging all streams in that tier."),
            ChangelogFeature(Icons.Filled.PlayCircle, "Reliable Replay for Probed Streams", "Previously played streams now fall back to server transcoding if direct play fails before the first frame, normalize comma-separated container names, and cap placeholder detection to short clips under 3 minutes."),
        ),
    ),
    ChangelogRelease(
        version = "0.46.4",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.46.4",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Accurate Placeholder Filtering", "Unprobed stubs with default Unknown video ranges or sub-megabyte stub sizes are now cleanly recognized as empty and routed to the Digital Release Filter card."),
            ChangelogFeature(Icons.Filled.Speed, "Instant Stream Picker Opening", "Opening the stream picker no longer blocks on a duplicate pre-check when no stream is remembered, and refreshing bypasses the local source cache."),
        ),
    ),
    ChangelogRelease(
        version = "0.46.3",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.46.3",
        features = listOf(
            ChangelogFeature(Icons.Filled.Tune, "Always Show Stream Picker", "Removed automatic playback when only a single stream is returned so you can always inspect or refresh the available source first."),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Overscan Calibration Modal", "Wrapped the overscan calibration screen in a full-screen dialog for reliable focus and display bounds."),
        ),
    ),
    ChangelogRelease(
        version = "0.46.2",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.46.2",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Digital Release Filter Card", "When no playable streams are found, the stream picker now displays a Digital Release Filter card with refresh and provider controls."),
            ChangelogFeature(Icons.Filled.Speed, "Column-Locked Grid Scrolling", "Vertical D-pad navigation in View All and Search grids stays locked to your current column without drifting sideways."),
            ChangelogFeature(Icons.Filled.Tune, "Cover Art Fallback Chain", "Poster and landscape cards now step through Primary, Thumb, Backdrop, and Series image tags and preserve CDN redirects so cover art loads reliably."),
        ),
    ),
    ChangelogRelease(
        version = "0.46.1",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.46.1",
        features = listOf(
            ChangelogFeature(Icons.Filled.Speed, "Snappy Remote Scrolling", "Remote navigation and focus scrolling now respond in 90ms with comfortable viewing margins."),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Instant Episode & Movie Loading", "Initial player loading no longer queries trickplay sheets up front, starting video playback without delay."),
            ChangelogFeature(Icons.Filled.Tune, "Stream Detection Fix", "Refined stream detection preserves .strm and debrid releases while cleanly filtering placeholder stubs."),
        ),
    ),
    ChangelogRelease(
        version = "0.46.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.46.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Placeholder Stream Protection", "Titles without playable releases now cleanly open the stream picker with digital release availability hints instead of failing on stubs."),
            ChangelogFeature(Icons.Filled.Speed, "Stream Reliability", "Improved source resolution and fuzzy title matching across unreleased and stub-backed library titles."),
        ),
    ),
    ChangelogRelease(
        version = "0.45.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.45.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.Speed, "Stream Performance", "Performance enhancements and stream resolution updates."),
        ),
    ),
    ChangelogRelease(
        version = "0.44.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.44.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Complete Version History", "Settings > About now features complete collapsible release notes across all previous versions."),
            ChangelogFeature(Icons.Filled.Tune, "Notification Cleanup", "Stale changelog and server alerts are automatically cleaned up on major updates."),
        ),
    ),
    ChangelogRelease(
        version = "0.43.1",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.43.1",
        features = listOf(
            ChangelogFeature(Icons.Filled.PlayCircle, "Player", "Scope preferredAudio variable in load"),
        ),
    ),
    ChangelogRelease(
        version = "0.43.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.43.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.Speed, "Instant Next Episode Autoplay", "Pre-warms video buffers and stream negotiation in the background before the current episode ends for zero-delay binge watching."),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Expandable Version History", "Settings > About now features cleanly organized, collapsible release notes for past versions."),
        ),
    ),
    ChangelogRelease(
        version = "0.42.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.42.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.Bedtime, "Dialogue Boost / Night Mode", "Enhances spoken voices and softens loud audio during nighttime viewing, accessible in player audio settings."),
            ChangelogFeature(Icons.Filled.GraphicEq, "Audio Passthrough (HDMI)", "Direct bitstream audio output over HDMI for AV receivers and surround sound setups."),
            ChangelogFeature(Icons.Filled.Mic, "Remote Voice Search", "Speak directly into your remote control microphone to search your entire media library."),
            ChangelogFeature(Icons.Filled.Tv, "Picture-in-Picture", "Seamlessly continue watching media in a floating window while navigating away from the player."),
            ChangelogFeature(Icons.Filled.Speed, "Faster Performance", "Ahead-of-Time baseline profiles and optimized media caching deliver fluid 60fps scrolling."),
        ),
    ),
    ChangelogRelease(
        version = "0.41.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.41.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.Group, "What Others Are Watching", "A new home screen row shows what friends and family are streaming in real-time or recently completed, featuring viewer avatars on every card."),
            ChangelogFeature(Icons.Filled.Tune, "Recommendations Settings", "A new dedicated Settings tab lets you choose which users appear in your social watching row, or toggle the row off entirely."),
            ChangelogFeature(Icons.Filled.Palette, "Genre Row Customization", "Personalize your home screen feed by choosing and reordering your favorite genre recommendation rows."),
            ChangelogFeature(Icons.Filled.Speed, "Faster Performance", "Parallel row loading and stream prefetching deliver faster startup and fluid navigation across all your media."),
        ),
    ),
    ChangelogRelease(
        version = "0.40.6",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.40.6",
        features = listOf(
            ChangelogFeature(Icons.Filled.Tv, "Home", "Show What Others Are Watching again"),
        ),
    ),
    ChangelogRelease(
        version = "0.40.5",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.40.5",
        features = listOf(
            ChangelogFeature(Icons.Filled.Tv, "Home", "Describe the per-row catalog dedupe"),
            ChangelogFeature(Icons.Filled.Tv, "Home", "Show each row as soon as its own data arrives"),
        ),
    ),
    ChangelogRelease(
        version = "0.40.4",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.40.4",
        features = listOf(
            ChangelogFeature(Icons.Filled.Tv, "Home", "Don't wait on the community row, use the server's cached genres"),
        ),
    ),
    ChangelogRelease(
        version = "0.40.3",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.40.3",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add 'What Others Are Watching' recommendation row and recommendations settings"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Launcher", "Use the show's art when an episode doesn't carry it"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Watchnext", "Refresh art and text on next-episode cards"),
        ),
    ),
    ChangelogRelease(
        version = "0.40.2",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.40.2",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Streams", "Never start the player without a stream"),
        ),
    ),
    ChangelogRelease(
        version = "0.40.1",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.40.1",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Detail", "Plain loading screen and no stream line"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Detail", "Reopen title pages instantly from what was last seen"),
        ),
    ),
    ChangelogRelease(
        version = "0.40.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.40.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Launcher", "Banner with the Jellio wordmark"),
        ),
    ),
    ChangelogRelease(
        version = "0.39.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.39.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Launcher", "Replace the Recently Added channel with Top Picks for You"),
        ),
    ),
    ChangelogRelease(
        version = "0.38.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.38.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Launcher", "Home screen channels for Continue Watching, Next Up, Watchlist and Recently Added"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Show art and descriptions on Continue Watching cards"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Detail", "Open title pages without waiting for streams"),
            ChangelogFeature(Icons.Filled.Tv, "Home", "Keep episode stills on the app's own episode cards"),
        ),
    ),
    ChangelogRelease(
        version = "0.37.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.37.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Watchnext", "Add the next episode of shows being watched"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Watchnext", "Set the content id, one entry per series, show sync status"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Watchnext", "Publish at most five, unfinished titles first"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Streams", "Stop resolving the same title over and over"),
            ChangelogFeature(Icons.Filled.Speed, "Performance", "Preload title pages and the next episode's streams"),
        ),
    ),
    ChangelogRelease(
        version = "0.36.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.36.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.Settings, "Settings", "Match the web settings look"),
            ChangelogFeature(Icons.Filled.Tv, "Home", "Build rows a screen ahead and stop per-frame redraws while scrolling"),
        ),
    ),
    ChangelogRelease(
        version = "0.35.1",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.35.1",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Watchnext", "Show Continue Watching on the Google TV home"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Calendar", "Say watched shows appear too when nothing is upcoming"),
        ),
    ),
    ChangelogRelease(
        version = "0.35.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.35.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Seasonal", "Still and off levels, lighter reduced scenes"),
            ChangelogFeature(Icons.Filled.Tv, "Home", "Open a row's view all as a poster grid"),
            ChangelogFeature(Icons.Filled.Settings, "Settings", "Split settings into Account, Playback, Display and About categories"),
            ChangelogFeature(Icons.Filled.Tv, "Home", "Enter rows on their first or last used card"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Ui", "Give resting buttons a visible pill again"),
        ),
    ),
    ChangelogRelease(
        version = "0.34.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.34.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.Mic, "Search", "Show results as they arrive, library titles first"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Ui", "Stop solid buttons from looking focused at rest"),
            ChangelogFeature(Icons.Filled.Tv, "Home", "Build rows ahead so scrolling between them stays smooth"),
        ),
    ),
    ChangelogRelease(
        version = "0.33.1",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.33.1",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Display", "Trap focus in overscan calibration and render at true full-screen root"),
        ),
    ),
    ChangelogRelease(
        version = "0.33.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.33.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.Settings, "Settings", "Add TV overscan safe area calibration and eliminate 4K scroll lag"),
        ),
    ),
    ChangelogRelease(
        version = "0.32.2",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.32.2",
        features = listOf(
            ChangelogFeature(Icons.Filled.PlayCircle, "Player", "Show the subtitle track that was picked"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Detail", "Show the bouncing title loader instead of a stretched still"),
            ChangelogFeature(Icons.Filled.PlayCircle, "Player", "Contrast on next episode button and overlay rendering during playback"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Detail", "Open title pages as fast as on web"),
            ChangelogFeature(Icons.Filled.Tv, "Home", "Stabilize models and memoize row callbacks for smooth 4K scrolling"),
        ),
    ),
    ChangelogRelease(
        version = "0.32.1",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.32.1",
        features = listOf(
            ChangelogFeature(Icons.Filled.PlayCircle, "Player", "Clearer stream badges, HD detection and failover notice"),
        ),
    ),
    ChangelogRelease(
        version = "0.32.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.32.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.PlayCircle, "Player", "Follow the server's video transcode policy"),
            ChangelogFeature(Icons.Filled.PlayCircle, "Player", "Mark streams that need a video re-encode in the Sources panel"),
        ),
    ),
    ChangelogRelease(
        version = "0.31.1",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.31.1",
        features = listOf(
            ChangelogFeature(Icons.Filled.PlayCircle, "Player", "Episode panel opens on the current episode"),
        ),
    ),
    ChangelogRelease(
        version = "0.31.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.31.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.PlayCircle, "Player", "Try the next stream when one doesn't play"),
        ),
    ),
    ChangelogRelease(
        version = "0.30.1",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.30.1",
        features = listOf(
            ChangelogFeature(Icons.Filled.PlayCircle, "Player", "Remember subtitles between episodes"),
        ),
    ),
    ChangelogRelease(
        version = "0.30.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.30.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.PlayCircle, "Player", "End screen and a tidier Up Next card"),
        ),
    ),
    ChangelogRelease(
        version = "0.29.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.29.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.PlayCircle, "Player", "Pre-buffer setting with deeper loading while paused"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Docs", "Explain installing on Fire TV and Google TV with Downloader"),
        ),
    ),
    ChangelogRelease(
        version = "0.28.2",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.28.2",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Profile menu buttons wrap instead of squeezing, focused panel buttons no longer clipped"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Streams", "Recognise Gelato's placeholder by the title's own name"),
        ),
    ),
    ChangelogRelease(
        version = "0.28.1",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.28.1",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Smaller Home skeletons, a bell for Notifications, panels that fit"),
        ),
    ),
    ChangelogRelease(
        version = "0.28.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.28.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Group Watch and Notifications panels in the profile menu"),
            ChangelogFeature(Icons.Filled.Tv, "Home", "Show Home right away with loading skeletons"),
        ),
    ),
    ChangelogRelease(
        version = "0.27.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.27.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Group Watch on TV"),
        ),
    ),
    ChangelogRelease(
        version = "0.26.2",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.26.2",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Detail", "Keep focus off the sidebar while a title loads"),
            ChangelogFeature(Icons.Filled.Speed, "Performance", "Open to Home sooner"),
        ),
    ),
    ChangelogRelease(
        version = "0.26.1",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.26.1",
        features = listOf(
            ChangelogFeature(Icons.Filled.Settings, "Settings", "Fold the password fields behind a Change password button"),
        ),
    ),
    ChangelogRelease(
        version = "0.26.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.26.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Update", "Check for updates in the background"),
        ),
    ),
    ChangelogRelease(
        version = "0.25.2",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.25.2",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Streams", "Say no streams are available instead of offering Gelato's placeholder"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Streams", "Show no streams in the picker from every play button"),
        ),
    ),
    ChangelogRelease(
        version = "0.25.1",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.25.1",
        features = listOf(
            ChangelogFeature(Icons.Filled.Speed, "Performance", "Share one prefetch across cards and install bundled performance profiles"),
        ),
    ),
    ChangelogRelease(
        version = "0.25.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.25.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Show an announcement's picture in its toast"),
        ),
    ),
    ChangelogRelease(
        version = "0.24.2",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.24.2",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "All feed type chips back on the TV, with books, manga and audiobooks off by default"),
        ),
    ),
    ChangelogRelease(
        version = "0.24.1",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.24.1",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Library nav buttons use the same icons as Jellio web"),
        ),
    ),
    ChangelogRelease(
        version = "0.24.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.24.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Server announcements as toasts on every screen, and stay awake while playing"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Left/Right jump without bringing up the player controls"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Subtitle selection that actually sticks, and say why when it can't"),
        ),
    ),
    ChangelogRelease(
        version = "0.23.2",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.23.2",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Video freezing after stream changes, audio and subtitle selection"),
        ),
    ),
    ChangelogRelease(
        version = "0.23.1",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.23.1",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Drop Watching now and keep books, manga and audiobooks out of the TV feed"),
        ),
    ),
    ChangelogRelease(
        version = "0.23.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.23.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Libraries as their own nav buttons, Watchlist rows on Home, anime kept to the Anime library"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Feed filters by content type, books, manga and audiobooks off by default"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Call shows Shows everywhere, matching the server's library name"),
        ),
    ),
    ChangelogRelease(
        version = "0.22.1",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.22.1",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Skip buttons, stream switch feedback and remote focus on Home and the nav rail"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Keep playback going through audio switches and seeks, and make carousels one selectable card"),
        ),
    ),
    ChangelogRelease(
        version = "0.22.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.22.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Smaller, centered launcher icon on a slate background"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Episode sleep timer options and stream filters in the player's sources panel"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Watching now screen showing who plays what on which device"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Load images when the server address was saved with an uppercase scheme"),
            ChangelogFeature(Icons.Filled.Speed, "Performance", "Detect slower devices and calm the seasonal scenes there, plus an auto-skip intros setting"),
        ),
    ),
    ChangelogRelease(
        version = "0.21.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.21.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Navigable player controls with skip buttons and a scrubbable seek bar"),
        ),
    ),
    ChangelogRelease(
        version = "0.20.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.20.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Port the full web seasonal scenes for Halloween, Christmas and New Year"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add an image loading test to settings"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add quality filter chips that combine with language chips in the stream picker"),
            ChangelogFeature(Icons.Filled.Speed, "Performance", "Only log image and API requests in debug builds"),
        ),
    ),
    ChangelogRelease(
        version = "0.19.6",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.19.6",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Keep the remote usable on empty screens and open the nav rail at Home"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Show the title's artwork while a stream loads and explain playback errors"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Use the focus direction property the focus enter scope provides"),
        ),
    ),
    ChangelogRelease(
        version = "0.19.5",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.19.5",
        features = listOf(
            ChangelogFeature(Icons.Filled.Info, "Release Notes", "### Changes in 0.19.5  � **Audio & Language Menu Redesign**: Replaced the awkward top-right floating menu with an elegant, full-height right-side TV Leanback drawer matching the Subtitles drawer. Features semi-transparent scrim backdrop, clear header title, and auto-focuses the currently active audio track upon opening. � **All Player Overlays Aligned**: Standardized Sources, Episodes, Playback Speed, and Sleep Timer menus to use clean right-aligned drawer panels with scrim backdrops for a unified 10-foot TV experience. � **Audio Track Switching & Server Re-negotiation**: Fixed audio track selection in `PlayerViewModel` by removing the `directPlay` short-circuit that skipped server negotiation. Selecting an audio track now immediately re-negotiates with Jellyfin (`resolvePlayback`) at the current playback position. � **Seamless Mid-Stream Reloading**: Fixed ExoPlayer stream reload lifecycle in `PlayerScreen` to prepare and buffer directly at the current playback timestamp, preserving play state without triggering the initial \"Resume or Start Over\" dialog. � **Multi-Audio Track Selection**: Implemented robust audio track matching across all ExoPlayer audio groups and formats by language code and ordinal index, ensuring exact track selection overrides."),
        ),
    ),
    ChangelogRelease(
        version = "0.19.4",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.19.4",
        features = listOf(
            ChangelogFeature(Icons.Filled.Info, "Release Notes", "### Changes in 0.19.4 - **Home Hero Gap**: Fixed empty gap above hero section by removing padding on LazyColumn; hero section now sits flush at the screen top ( = 0\$). - **Seasonal Animations**: Restored GPU-accelerated animated seasonal layer (glowing floating embers, drifting bats, ghosts, falling snow, bubbles, hearts) running smoothly at 60 FPS on Compose Canvas on top of the ambient dual radial gradient wash. - **Image Loading on Google TV & Local Networks**: Added explicit network security configuration allowing cleartext HTTP on local subnets (Android 14 / targetSdk 36 compatibility), added synchronous SharedPreferences backing in SessionManager to prevent OkHttp interceptor deadlocks on launch, configured LAN SSL tolerance, and passed dual ApiKey/api_key query parameters. - **Playback Error 500 Fix**: Enforced SupportsDirectPlay requirement in canDirectPlay to prevent invalid Static=true direct streaming requests, added ApiKey query parameter, and configured ExoPlayer's DefaultHttpDataSource with authenticated Authorization and X-Emby-Token headers."),
        ),
    ),
    ChangelogRelease(
        version = "0.19.3",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.19.3",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Highlights & Features", "**Player Overlay Navigation & Stream Change Fix**: Fixed focus trapping where opening \"Change Stream\" or other player menus trapped D-pad focus in bottom player controls. Overlays now immediately autofocus their first item on mount, disable background control focus while open, and dismiss cleanly with Back button or tap gestures."),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Highlights & Features", "**High-Contrast TV Focus Rings**: Added crisp 2.5dp and 2.0dp white focus borders and brightened container highlights across PosterCard, LandscapeCard, StreamPicker cards, Sidebar navigation, Detail action buttons, and Settings rows so the selected element is unmistakably visible on 10-foot TV displays."),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Highlights & Features", "**Media Tech Badges**: Ported technical media badges from Jellio Web (4K UHD, 1080p, 720p, Dolby Vision, HDR10+, HDR10, HEVC, AV1, Dolby Atmos, DTS:X, TrueHD, DTS-HD, 7.1, 5.1, Stereo) and mounted them on DetailScreen and the Home Hero carousel."),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Highlights & Features", "**Home Hero Actions & TV D-Pad Navigation**: Upgraded the Home Hero carousel to include primary Play (direct playback), Watchlist (bookmark toggle with live state sync), and View Details buttons with seamless horizontal D-pad navigation across previous chevron, action buttons, and next chevron."),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Highlights & Features", "**Seasonal Recommendations Row**: Ported seasonal recommendations matching active seasonal themes (e.g. \"Spooky Season\" with unplayed horror and mystery titles during Halloween), dynamically rendered below Top Picks on the Home screen."),
        ),
    ),
    ChangelogRelease(
        version = "0.19.2",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.19.2",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Maintenance", "Internal improvements and bug fixes."),
        ),
    ),
    ChangelogRelease(
        version = "0.19.1",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.19.1",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Fix media playback remote button default focus to pause on click"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Fix audio playback by acquiring media audio focus and transcoding unsupported codecs to AAC"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Fix Coil 3 image loading by configuring OkHttp network fetcher and auth query parameter for thumbnails and avatars"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Sign published APKs with repository keystore secrets so updates install smoothly over existing installs"),
        ),
    ),
    ChangelogRelease(
        version = "0.19.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.19.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Expose playback to the system media session"),
        ),
    ),
    ChangelogRelease(
        version = "0.18.27",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.18.27",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Maintenance", "Internal improvements and bug fixes."),
        ),
    ),
    ChangelogRelease(
        version = "0.18.26",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.18.26",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Maintenance", "Internal improvements and bug fixes."),
        ),
    ),
    ChangelogRelease(
        version = "0.18.25",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.18.25",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Maintenance", "Internal improvements and bug fixes."),
        ),
    ),
    ChangelogRelease(
        version = "0.18.24",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.18.24",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Maintenance", "Internal improvements and bug fixes."),
        ),
    ),
    ChangelogRelease(
        version = "0.18.23",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.18.23",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Maintenance", "Internal improvements and bug fixes."),
        ),
    ),
    ChangelogRelease(
        version = "0.18.22",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.18.22",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Give the stream list a focusRestorer() instead of forcing requestFocus()"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Replace stream picker's LazyColumn with a plain Column"),
            ChangelogFeature(Icons.Filled.Speed, "Performance", "Switch audio tracks locally on a Direct Play source instead of re-transcoding"),
            ChangelogFeature(Icons.Filled.Speed, "Performance", "Prefetch Gelato streams on poster focus"),
            ChangelogFeature(Icons.Filled.Speed, "Performance", "Split search into independent Movies/Series requests"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Maintenance", "Add temporary Logcat diagnostics for stream picker Down navigation"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Maintenance", "Add temporary diagnostic isolating whether SourceCard can ever take programmatic focus"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Maintenance", "Test focusRestorer() as the reason direct requestFocus() into SourceCard does nothing"),
        ),
    ),
    ChangelogRelease(
        version = "0.18.21",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.18.21",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Track stream picker top-section focus per-leaf, not via an ancestor Column"),
        ),
    ),
    ChangelogRelease(
        version = "0.18.20",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.18.20",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Intercept stream picker Down/Up at the Activity's dispatchKeyEvent"),
        ),
    ),
    ChangelogRelease(
        version = "0.18.19",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.18.19",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Force stream picker nav through a top-level key handler, whiten hub logos"),
        ),
    ),
    ChangelogRelease(
        version = "0.18.18",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.18.18",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Use onPreviewKeyEvent so stream picker down-navigation actually fires"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Keep sidebar nav visible on Detail/Person/Service pages"),
        ),
    ),
    ChangelogRelease(
        version = "0.18.17",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.18.17",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Force stream picker focus navigation, fix language chip focus scale"),
        ),
    ),
    ChangelogRelease(
        version = "0.18.16",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.18.16",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Stream picker up-navigation/contrast, rebuild player controls as one pill"),
        ),
    ),
    ChangelogRelease(
        version = "0.18.15",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.18.15",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Studio hub logos, watchlist icon, profile/nav focus, privacy toggle, stream picker"),
        ),
    ),
    ChangelogRelease(
        version = "0.18.14",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.18.14",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Maintenance", "Fix commit type on the Now Playing removal for conventional-commits CI"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Other", "Now Playing feature entirely"),
        ),
    ),
    ChangelogRelease(
        version = "0.18.13",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.18.13",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Restore focus after popovers close, unbreak overflowing lists, drop redundant back UI"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Hoist changeAvatarFocusRequester out of the scrollable Column"),
        ),
    ),
    ChangelogRelease(
        version = "0.18.12",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.18.12",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Physically block scroll while hero/coverflow holds focus, not just react to it"),
        ),
    ),
    ChangelogRelease(
        version = "0.18.11",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.18.11",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Reactively watch scroll position instead of guessing a correction delay"),
        ),
    ),
    ChangelogRelease(
        version = "0.18.10",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.18.10",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Replace indefinite scroll-to-top polling with two targeted assertions"),
        ),
    ),
    ChangelogRelease(
        version = "0.18.9",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.18.9",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Keep reasserting scroll-to-top for as long as the hero/coverflow holds focus"),
        ),
    ),
    ChangelogRelease(
        version = "0.18.8",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.18.8",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Close arrow-to-View-Details focus jitter on Home hero and Library coverflow"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Correct focusProperties left/right assignment type"),
        ),
    ),
    ChangelogRelease(
        version = "0.18.7",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.18.7",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Reassert scroll-to-top a few times to close a remaining race on entry"),
        ),
    ),
    ChangelogRelease(
        version = "0.18.6",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.18.6",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Redirect coverflow entry focus declaratively instead of racing it"),
        ),
    ),
    ChangelogRelease(
        version = "0.18.5",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.18.5",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Decouple hero info from its buttons, bottom-align library arrows, claim initial coverflow focus"),
        ),
    ),
    ChangelogRelease(
        version = "0.18.4",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.18.4",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Align hero/coverflow arrows with View Details, fix Left/Right and stale scroll on library switch"),
        ),
    ),
    ChangelogRelease(
        version = "0.18.3",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.18.3",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Un-distort the library coverflow, replace filter chips with fields"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Correct an invalid Modifier.padding overload"),
        ),
    ),
    ChangelogRelease(
        version = "0.18.2",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.18.2",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Shrink the library coverflow so the first row fits on initial open"),
        ),
    ),
    ChangelogRelease(
        version = "0.18.1",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.18.1",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Suppress default bring-into-view instead of racing it, bump Settings icon further"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Opt in to the experimental BringIntoViewSpec API"),
        ),
    ),
    ChangelogRelease(
        version = "0.18.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.18.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Port the full jellio-mark-animated.svg to the boot splash"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Profile navigation, watched-state refresh, contrast sweep round 2"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Store jellyfish path-morph frames as parsed strings, not array literals"),
        ),
    ),
    ChangelogRelease(
        version = "0.17.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.17.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Port the real wake-trail bubbles onto the boot splash jellyfish"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Coverflow arrows hidden behind zIndex, ongoing scroll-pin, icon"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Use the real web loading spinner instead of the sweep bar"),
        ),
    ),
    ChangelogRelease(
        version = "0.16.2",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.16.2",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Carousel focus jitter, Anime coverflow arrows, account switcher"),
        ),
    ),
    ChangelogRelease(
        version = "0.16.1",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.16.1",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Sidebar nav parity and profile/settings cleanup"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Trap focus inside overlays, animated avatars, always show Anime"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Give plain elevated surfaces an explicit focused contrast pair"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Hero carousel focus/scroll, profile badges focus trap"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Add missing fillMaxSize import in HeroSection"),
        ),
    ),
    ChangelogRelease(
        version = "0.16.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.16.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add a Feed screen and rebuild Profile with achievements, banner and bio"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add a Privacy toggle and a View profile row to Settings"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Credit real watches and mark native played state from the player"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Correct Surface shape wrapping and a Dp*Int order bug in ProfileScreen"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Enlarge Settings sidebar icon to match its siblings"),
        ),
    ),
    ChangelogRelease(
        version = "0.15.1",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.15.1",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "TV polish pass � Group Watch removal, sidebar, focus, contrast (#34)"),
        ),
    ),
    ChangelogRelease(
        version = "0.15.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.15.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Populate Google TV's Watch Next row from Continue Watching (#33)"),
        ),
    ),
    ChangelogRelease(
        version = "0.14.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.14.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add Remove Show from Up Next, hiding the whole series (#32)"),
        ),
    ),
    ChangelogRelease(
        version = "0.13.1",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.13.1",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Authenticate avatar preset image requests with api_key (#31)"),
        ),
    ),
    ChangelogRelease(
        version = "0.13.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.13.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Group preset avatars by their own real category (#30)"),
        ),
    ),
    ChangelogRelease(
        version = "0.12.1",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.12.1",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Docs", "Add a session handoff doc for the next agent (#29)"),
        ),
    ),
    ChangelogRelease(
        version = "0.12.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.12.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Real app icon and animated vector boot splash mark (#28)"),
        ),
    ),
    ChangelogRelease(
        version = "0.11.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.11.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Replace pill nav with a collapsible sidebar rail (#27)"),
        ),
    ),
    ChangelogRelease(
        version = "0.10.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.10.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Move row \"view all\" off the title onto its own button (#26)"),
        ),
    ),
    ChangelogRelease(
        version = "0.9.2",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.9.2",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Real down-navigation and Settings scroll regressions (#25)"),
        ),
    ),
    ChangelogRelease(
        version = "0.9.1",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.9.1",
        features = listOf(
            ChangelogFeature(Icons.Filled.Speed, "Performance", "Serve the boot splash video as webm (#24)"),
        ),
    ),
    ChangelogRelease(
        version = "0.9.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.9.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Play the jellyfish logo reveal instead of the plain boot splash"),
        ),
    ),
    ChangelogRelease(
        version = "0.8.2",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.8.2",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Drop the one invented accent color, checked against Nuvio directly"),
        ),
    ),
    ChangelogRelease(
        version = "0.8.1",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.8.1",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Ask for the server address before username and password"),
        ),
    ),
    ChangelogRelease(
        version = "0.8.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.8.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Check GitHub for new releases and offer an in-app update"),
        ),
    ),
    ChangelogRelease(
        version = "0.7.1",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.7.1",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Match pill's mobile tab order, prefetch Home behind a boot splash"),
        ),
    ),
    ChangelogRelease(
        version = "0.7.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.7.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Close mobile-web parity gaps found in a fresh comparison pass"),
        ),
    ),
    ChangelogRelease(
        version = "0.6.1",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.6.1",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Full-bleed pill navigation, icon-mode fade, reactive card sizing"),
        ),
    ),
    ChangelogRelease(
        version = "0.6.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.6.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Port the detail hero's own collapse/expand action row (#16)"),
        ),
    ),
    ChangelogRelease(
        version = "0.5.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.5.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Long-press card options, row modal navigation, Customize mode lock, pill polish (#15)"),
        ),
    ),
    ChangelogRelease(
        version = "0.4.7",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.4.7",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Revert", "Drop the graphicsLayer full-bleed offset (#14)"),
        ),
    ),
    ChangelogRelease(
        version = "0.4.6",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.4.6",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Cold-start focus, library picker trap, immersive screen focus, full-bleed gap (#13)"),
        ),
    ),
    ChangelogRelease(
        version = "0.4.5",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.4.5",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Clear the nav pill's own bounds on Home and Library (#12)"),
        ),
    ),
    ChangelogRelease(
        version = "0.4.4",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.4.4",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Give scrollable screens a real default D-pad entry point (#11)"),
        ),
    ),
    ChangelogRelease(
        version = "0.4.3",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.4.3",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Drop focusGroup blocking default entry into scroll containers (#10)"),
        ),
    ),
    ChangelogRelease(
        version = "0.4.2",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.4.2",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Drop the broken shared focus-requester down override (#9)"),
        ),
    ),
    ChangelogRelease(
        version = "0.4.1",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.4.1",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Coverflow anime leak, exact Library icon, real nav pill centering fix (#8)"),
        ),
    ),
    ChangelogRelease(
        version = "0.4.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.4.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Match web's real three-wide overlapping coverflow stage (#7)"),
        ),
    ),
    ChangelogRelease(
        version = "0.3.2",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.3.2",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "D-pad dead end, Group Watch focus trap, nav pill centering, Library icon (#6)"),
        ),
    ),
    ChangelogRelease(
        version = "0.3.1",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.3.1",
        features = listOf(
            ChangelogFeature(Icons.Filled.Speed, "Performance", "Add an in-memory response cache to JellioRepository"),
        ),
    ),
    ChangelogRelease(
        version = "0.3.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.3.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add sort and genre filter chips to the library's main row"),
        ),
    ),
    ChangelogRelease(
        version = "0.2.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.2.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Wire the app to a real Jellio-Plugin backend"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Extend data layer for detail, playback, search and library"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Real navigation stack, Detail/Player routes, card click-through"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add the Detail screen and stream picker"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add real playback with Media3 ExoPlayer"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add Search, Watchlist, Library, Settings and Calendar screens"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add Person screen, remember-stream-choice, Change Stream button"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Port the real recommendation engine to Home"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add subtitle support to the player"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add Settings language preference section"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add library coverflow carousel"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add Home Coming Soon and catalog rows"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add Studio Hub strip and Service screen"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add Home genre rows, drop per-library stand-in"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add ServiceScreen genre/type filter chips"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Stream picker cards gain bitrate/description/language tags"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Stream picker gains a Resume button and backdrop fallback"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Stream picker gains a language filter chip bar"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Shows library coverflow gets its real time-of-day editorial text"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Nav pill collapses to icons on scroll, real mobile-nav parity"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Home never had its own real greeting header"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Continue Watching/Up Next render as plain posters, not landscape"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add per-card watchlist/watched quick actions to Home poster rows"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add player pause overlay and resume/start-over prompt"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add player Up Next overlay with countdown"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add Intro Skipper skip button and Credits-aware Up Next timing"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add row-title browse-all modal to Home and Library"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add Now Playing panel showing active server sessions"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Wire fetchAll into Home's row-list modal for catalog/genre rows"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add player Speed selector"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add player sleep timer"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add player audio track switching"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add in-player source switching panel"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add in-player episodes side panel"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add trickplay scrub preview to seek"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add player toast chrome for re-negotiation failures"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Wire row list modal into service screen"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Wire fetchAll into library rows"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add home row customization"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Surface sleep timer status in settings"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add avatar picker to settings"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add context-aware card options menu"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add Group Watch membership and chat"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Surface admin dashboard link in settings"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add subtitle style controls to the player"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add who's watching profile picker to login"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add forgot password flow to login"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add shard shatter animation to card removal"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add seasonal effects drift particle overlay"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add Friday the 13th seasonal effect"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add frost seasonal effect"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add film noir seasonal effect"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add New Year fireworks seasonal effect"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add star wars hyperspace seasonal effect"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Rotate the home hero through every real candidate"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Add Remove from Library to the home card options menu"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Wire the card options menu into Library, Watchlist, Search and Service"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Hero backdrop, TV-scale sizing, consolidate library nav"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Clamp pill height, port real Anime library detection"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Let D-pad down escape the top nav pill into home content"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Source hero from real Movie/Series candidates, add Up Next row"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Correct focusGroup's real package"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Tv-material3 has no CircularProgressIndicator, ColumnScope typo"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Missing MediaStreamDto import, invalid padding overload"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Wrap coverflow dot's shape in ClickableSurfaceDefaults"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Detail screen real gaps against screens/detail.js"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Anime library regresses to full unfiltered TV catalog"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "PosterCard never shows watched/progress state"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "StreamPickerOverlay crashes unhandled on a load failure"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "A failed search looks identical to no results"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "D-pad Down from the nav pill only ever worked on Home"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Home and library loads sequentially awaited every real fetch"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Row titles missing their real underline accent"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Nav pill clips Settings and its own selection reads too small"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "CI failure, missing padding import in HomeScreen"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Wire onPlayDirect/onPlayManually into the Home route, not Library"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Drop stray weight import shadowing the real ColumnScope/RowScope extension"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Correct graphicsLayer import in card shatter animation"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Import the animateFloat extension in Friday 13th overlay"),
        ),
    ),
    ChangelogRelease(
        version = "0.1.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.1.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Feature", "Scaffold the Android TV client"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Migrate jvmTarget to the compilerOptions DSL"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Bump compileSdk/targetSdk to 36"),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Bug Fix", "Tolerate a no-op version bump in make release"),
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

    fun clearPreviousChangelogs(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_LAST_SEEN).apply()
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
                    .padding(end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                release.features.forEach { feature ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
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
                                text = "Previous Versions (" + previousReleases.size + ")",
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
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
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
                    .focusRequester(buttonFocusRequester),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Continue",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
    }
}
