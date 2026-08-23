package com.jellio.tv.ui.home

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import coil3.compose.AsyncImage
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.ui.theme.JellioBgElevated

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
        AsyncImage(
            model = imageUrl(item, "Primary", 400),
            contentDescription = item.Name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(PosterWidth).aspectRatio(2f / 3f),
        )
    }
}
