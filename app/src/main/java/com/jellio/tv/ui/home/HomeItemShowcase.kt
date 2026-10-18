package com.jellio.tv.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import com.jellio.tv.ui.theme.scaled
import java.util.Locale

// Dynamic top showcase driven by the currently focused item from any row.
// Displays the highlighted item's backdrop on the right with a smooth horizontal
// fade to black on the left and vertical fade at the bottom. The left side showcases
// the title clearlogo (with text fallback), primary metadata, ongoing status badge,
// and overview synopsis.
@Composable
fun HomeItemShowcase(
    item: BaseItemDto?,
    imageUrl: (BaseItemDto, String, Int) -> String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(JellioBg),
    ) {
        if (item != null) {
            val backdropCandidates = remember(item.Id) { posterBackdropCandidates(item, imageUrl, 1920) }
            val activeBackdropUrl = backdropCandidates.firstOrNull()

            if (!activeBackdropUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.74f)
                        .align(Alignment.TopEnd)
                        .drawWithCache {
                            val horizontalFadeEndX = size.width * 0.65f
                            val horizontalGradient = Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0.0f to JellioBg,
                                    0.22f to JellioBg.copy(alpha = 0.92f),
                                    0.45f to JellioBg.copy(alpha = 0.70f),
                                    0.72f to JellioBg.copy(alpha = 0.25f),
                                    1.0f to Color.Transparent,
                                ),
                                startX = 0f,
                                endX = horizontalFadeEndX,
                            )
                            val bottomFadeStartY = size.height * 0.45f
                            val verticalGradient = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Transparent,
                                    0.40f to JellioBg.copy(alpha = 0.40f),
                                    0.75f to JellioBg.copy(alpha = 0.85f),
                                    1.0f to JellioBg,
                                ),
                                startY = bottomFadeStartY,
                                endY = size.height,
                            )
                            onDrawWithContent {
                                drawContent()
                                drawRect(
                                    brush = horizontalGradient,
                                    topLeft = Offset(0f, 0f),
                                    size = Size(horizontalFadeEndX, size.height),
                                )
                                drawRect(
                                    brush = verticalGradient,
                                    topLeft = Offset(0f, bottomFadeStartY),
                                    size = Size(size.width, size.height - bottomFadeStartY),
                                )
                            }
                        },
                ) {
                    AsyncImage(
                        model = activeBackdropUrl,
                        contentDescription = item.Name,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // Info column on the left
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth(0.54f)
                    .padding(start = 48.dp, top = 20.dp, end = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // ClearLogo with fallback to bold Title
                val logoCandidates = remember(item.Id) { posterLogoCandidates(item, imageUrl, 500) }
                val logoUrl = logoCandidates.firstOrNull()
                var logoLoadFailed by remember(item.Id) { mutableStateOf(false) }

                if (!logoUrl.isNullOrBlank() && !logoLoadFailed) {
                    AsyncImage(
                        model = logoUrl,
                        contentDescription = item.Name,
                        onError = { logoLoadFailed = true },
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart,
                        modifier = Modifier
                            .height(70.dp.scaled())
                            .widthIn(min = 80.dp.scaled(), max = 220.dp.scaled()),
                    )
                } else {
                    Text(
                        text = item.Name ?: item.SeriesName ?: "",
                        style = MaterialTheme.typography.headlineLarge,
                        color = JellioText,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Metadata row: Type • Genre • Runtime • Year • [IMDb] 8.0
                val isSeries = item.Type.equals("Series", ignoreCase = true)
                val isOngoing = (isSeries && item.Status?.equals("Ended", ignoreCase = true) != true && item.EndDate == null) ||
                    item.Status?.equals("Continuing", ignoreCase = true) == true ||
                    item.Status?.equals("Ongoing", ignoreCase = true) == true


                val metadataParts = remember(item, isSeries, isOngoing) {
                    buildList {
                        val typeStr = when (item.Type?.lowercase()) {
                            "movie" -> "Movie"
                            "series" -> "Series"
                            "episode" -> "Episode"
                            else -> item.Type?.replaceFirstChar { it.uppercase() }
                        }
                        if (!typeStr.isNullOrBlank()) add(typeStr)

                        item.Genres?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { add(it) }

                        item.RunTimeTicks?.let { ticks ->
                            val totalMinutes = ticks / (10_000_000L * 60L)
                            if (totalMinutes > 0) {
                                val hours = totalMinutes / 60
                                val mins = totalMinutes % 60
                                val runtime = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
                                add(runtime)
                            }
                        }

                        val year = item.ProductionYear?.toString() ?: item.PremiereDate?.take(4)
                        if (!year.isNullOrBlank()) {
                            if (isSeries && isOngoing) {
                                add("$year-")
                            } else {
                                add(year)
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    metadataParts.forEachIndexed { idx, part ->
                        Text(
                            text = part,
                            style = MaterialTheme.typography.labelMedium,
                            color = JellioTextSecondary,
                            maxLines = 1,
                        )
                        if (idx < metadataParts.lastIndex || item.CommunityRating != null) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelSmall,
                                color = JellioTextSecondary.copy(alpha = 0.6f),
                            )
                        }
                    }

                    // IMDb yellow rating badge
                    item.CommunityRating?.let { rating ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFE5A00D), RoundedCornerShape(3.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "IMDb",
                                    color = Color.Black,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                    ),
                                )
                            }
                            Text(
                                text = String.format(Locale.US, "%.1f", rating),
                                style = MaterialTheme.typography.labelMedium,
                                color = JellioTextSecondary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                // Ongoing badge
                if (isOngoing) {
                    Box(
                        modifier = Modifier
                            .border(
                                BorderStroke(1.dp, Color.White.copy(alpha = 0.55f)),
                                RoundedCornerShape(6.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "ONGOING",
                            color = JellioText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp,
                            ),
                        )
                    }
                }

                // Overview synopsis
                item.Overview?.takeIf { it.isNotBlank() }?.let { overview ->
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = JellioTextSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 20.sp,
                    )
                }
            }
        }
    }
}
