package com.jellio.tv.ui.common

import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.basicMarquee
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.tv.material3.Text

// One line of text that scrolls sideways while focused when it doesn't fit
// (as on Nuvio's cards), and ends in "…" otherwise.
@Composable
fun FocusMarqueeText(
    text: String,
    focused: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
) {
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        maxLines = 1,
        overflow = if (focused) TextOverflow.Clip else TextOverflow.Ellipsis,
        modifier = if (focused) {
            modifier.basicMarquee(
                iterations = Int.MAX_VALUE,
                animationMode = MarqueeAnimationMode.Immediately,
                initialDelayMillis = 900,
                repeatDelayMillis = 1500,
            )
        } else {
            modifier
        },
    )
}
