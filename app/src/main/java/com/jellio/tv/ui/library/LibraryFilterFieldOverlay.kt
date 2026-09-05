package com.jellio.tv.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioBorder
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary

// Real screens/library.js's own <select> pair rendered here as one
// real field per real dropdown (a compact button carrying today's own
// current choice) rather than every option laid out as its own always
// visible chip: real feedback live asked for exactly this shape, the
// same one this screen's own web counterpart already has (a plain
// "Newest release ▾"/"All genres ▾" pair, real screenshot attached).
// Opening either field mounts LibraryFilterFieldOverlay below, this
// rail's own D-pad equivalent of that <select>'s own native popover.
data class LibraryFilterOption(val label: String, val value: String?)

@Composable
fun LibraryFilterField(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = JellioBgElevated,
            contentColor = JellioText,
            focusedContainerColor = Color.White.copy(alpha = 0.18f),
            focusedContentColor = JellioText,
        ),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = JellioTextSecondary,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

// Mirrors ui/nav/LibraryPickerOverlay.kt's own real modal shape (a
// dismiss scrim, a focus trap, initial focus onto this list's own
// first real entry) almost exactly, generic over whichever field
// opened it (sort or genre) rather than one bespoke overlay per field.
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LibraryFilterFieldOverlay(
    title: String,
    options: List<LibraryFilterOption>,
    selectedValue: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDismiss)

    val firstEntryFocusRequester = remember { FocusRequester() }
    LaunchedEffect(options) { firstEntryFocusRequester.requestFocus() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusProperties { exit = { FocusRequester.Cancel } },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(min = 280.dp, max = 360.dp)
                .heightIn(max = 420.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(JellioBgElevated)
                .border(1.dp, JellioBorder, RoundedCornerShape(16.dp))
                .padding(vertical = 12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = JellioTextSecondary,
                modifier = Modifier.padding(horizontal = 20.dp, bottom = 8.dp),
            )
            // Mirrors ui/nav/LibraryPickerOverlay.kt's own plain
            // Column+forEachIndexed exactly (that file's own header
            // covers the real focus-trap shape both share): a real
            // LazyColumn here would need this Column's own real parent
            // to hand it a bounded height constraint of its own, not
            // guaranteed just from a modifier chain alone the way this
            // dialog's own real heightIn(max) above only bounds this
            // outer Column, not implicitly whatever sits inside it. A
            // plain scrollable Column carries no such real requirement,
            // genre lists here are short enough it costs nothing.
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
                options.forEachIndexed { index, option ->
                    val isSelected = option.value == selectedValue
                    val isFirst = index == 0
                    Surface(
                        onClick = { onSelect(option.value) },
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.Transparent,
                            contentColor = JellioText,
                            focusedContainerColor = Color.White.copy(alpha = 0.18f),
                            focusedContentColor = JellioText,
                        ),
                        modifier = Modifier.fillMaxWidth().let {
                            if (isFirst) it.focusRequester(firstEntryFocusRequester) else it
                        },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
                        ) {
                            Text(
                                text = option.label,
                                color = if (isSelected) JellioSecondary else JellioText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 240.dp),
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = JellioSecondary,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
