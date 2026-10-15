package com.jellio.tv.ui.changelog

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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
        version = "0.42.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.42.0",
        features = listOf(
            ChangelogFeature(
                icon = Icons.Filled.Bedtime,
                title = "Dialogue Boost / Night Mode",
                description = "Enhances spoken voices and softens loud audio during nighttime viewing, accessible in player audio settings.",
            ),
            ChangelogFeature(
                icon = Icons.Filled.GraphicEq,
                title = "Audio Passthrough (HDMI)",
                description = "Direct bitstream audio output over HDMI for AV receivers and surround sound setups.",
            ),
            ChangelogFeature(
                icon = Icons.Filled.Mic,
                title = "Remote Voice Search",
                description = "Speak directly into your remote control microphone to search your entire media library.",
            ),
            ChangelogFeature(
                icon = Icons.Filled.Tv,
                title = "Picture-in-Picture",
                description = "Seamlessly continue watching media in a floating window while navigating away from the player.",
            ),
            ChangelogFeature(
                icon = Icons.Filled.Speed,
                title = "Faster Performance",
                description = "Ahead-of-Time baseline profiles and optimized media caching deliver fluid 60fps scrolling.",
            ),
        ),
    ),
    ChangelogRelease(
        version = "0.41.0",
        title = "What's New in Jellio TV",
        subtitle = "Version 0.41.0",
        features = listOf(
            ChangelogFeature(
                icon = Icons.Filled.Group,
                title = "What Others Are Watching",
                description = "A new home screen row shows what friends and family are streaming in real-time or recently completed, featuring viewer avatars on every card.",
            ),
            ChangelogFeature(
                icon = Icons.Filled.Tune,
                title = "Recommendations Settings",
                description = "A new dedicated Settings tab lets you choose which users appear in your social watching row, or toggle the row off entirely.",
            ),
            ChangelogFeature(
                icon = Icons.Filled.Palette,
                title = "Genre Row Customization",
                description = "Personalize your home screen feed by choosing and reordering your favorite genre recommendation rows.",
            ),
            ChangelogFeature(
                icon = Icons.Filled.Speed,
                title = "Faster Performance",
                description = "Parallel row loading and stream prefetching deliver faster startup and fluid navigation across all your media.",
            ),
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
        return lastSeen != targetRelease.version
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
) {
    BackHandler(onBack = onDismiss)
    val buttonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        repeat(10) {
            delay(50)
            if (runCatching { buttonFocusRequester.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
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
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Dismiss Button
            Surface(
                onClick = onDismiss,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.12f),
                    contentColor = JellioText,
                    focusedContainerColor = Color.White,
                    focusedContentColor = Color.Black,
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
                border = ClickableSurfaceDefaults.border(
                    focusedBorder = Border(
                        border = BorderStroke(2.dp, Color.White),
                        shape = RoundedCornerShape(12.dp),
                    )
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .focusRequester(buttonFocusRequester),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Got it",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
}
