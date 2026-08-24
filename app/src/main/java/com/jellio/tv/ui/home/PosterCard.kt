package com.jellio.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import coil3.compose.AsyncImage
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioText

private val PosterWidth = 170.dp

// Mirrors components/card.js's own poster shape: a 2:3 poster, no
// text underneath (the real title only shows up in the row's own
// heading and, later, a focused card's own detail panel), the same
// real reasoning card.js's own header gives for keeping a grid dense.
@Composable
fun PosterCard(
    item: BaseItemDto,
    imageUrl: (BaseItemDto, String, Int) -> String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = JellioBgElevated),
        modifier = modifier.width(PosterWidth),
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
            // that function's own branching uses.
            val userData = item.UserData
            if (userData?.Played == true) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = JellioText,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
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
