package com.jellio.tv.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary

// Real port of components/rowListModal.js's own openRowListModal():
// real feedback was that scrolling all the way across a long row (a
// studio hub's own "Series on Netflix", easily 20+ deep) by D-pad
// alone is tedious, the horizontal track itself giving no sense of how
// much further there is to go. A plain vertical list of every real
// item the row already loaded, one press through to it, rendered by
// the calling screen at its own root the same real reason
// CardOptionsMenu/EpisodeOptionsMenu already are.
//
// Real web's own fetchAll (a second, unbounded real network request
// swapping in a row's own full depth past its ROW_LIMIT once it lands)
// is not ported here yet: this shows exactly what the row itself
// already has loaded, real value on its own for every row already
// pulling 24 real items, deferred rather than guessed at.
@Composable
fun RowListModal(
    title: String,
    items: List<BaseItemDto>,
    imageUrl: (BaseItemDto, String, Int) -> String,
    onItemClick: (BaseItemDto) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.46f)
                .fillMaxHeight(0.82f)
                .background(JellioBgElevated, RoundedCornerShape(20.dp))
                .padding(vertical = 20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
            ) {
                Text(
                    text = title,
                    color = JellioText,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    onClick = onDismiss,
                    shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.1f), contentColor = JellioText),
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                    }
                }
            }
            LazyColumn(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                items(items, key = { it.Id }) { item ->
                    RowListItem(
                        item = item,
                        imageUrl = imageUrl,
                        onClick = {
                            onDismiss()
                            onItemClick(item)
                        },
                    )
                }
            }
        }
    }
}

// Real port of components/rowListModal.js's own itemSubtitle(): a
// production year first, "Series" appended for a Series or Season
// item, the same real distinction that file's own buildListItem() uses
// rather than a plain Movie/Series label every real item would carry.
private fun itemSubtitle(item: BaseItemDto): String {
    val bits = mutableListOf<String>()
    item.ProductionYear?.let { bits.add(it.toString()) }
    if (item.Type == "Series" || item.Type == "Season") bits.add("Series")
    return bits.joinToString(" · ")
}

@Composable
private fun RowListItem(
    item: BaseItemDto,
    imageUrl: (BaseItemDto, String, Int) -> String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, contentColor = JellioText),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            val tag = item.ImageTags?.get("Primary")
            if (tag != null) {
                AsyncImage(
                    model = imageUrl(item, "Primary", 160),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(56.dp).height(84.dp).clip(RoundedCornerShape(6.dp)),
                )
            } else {
                Box(modifier = Modifier.width(56.dp).height(84.dp).background(JellioBg, RoundedCornerShape(6.dp)))
            }
            Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                Text(text = item.Name.orEmpty(), color = JellioText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val subtitle = itemSubtitle(item)
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        color = JellioTextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = null, tint = JellioTextSecondary)
        }
    }
}
