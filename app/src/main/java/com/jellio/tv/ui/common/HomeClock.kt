package com.jellio.tv.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.jellio.tv.data.i18n.displayLocale
import com.jellio.tv.data.prefs.ClockStyle
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import kotlinx.coroutines.delay
import java.util.Calendar

data class ClockText(val time: String, val period: String?)

// 24-hour: "09:05". 12-hour: "9:05" plus the period ("AM"/"PM", in the locale's wording).
fun formatClock(hour: Int, minute: Int, style: ClockStyle, amPm: Array<String> = arrayOf("AM", "PM")): ClockText {
    val mm = minute.toString().padStart(2, '0')
    return if (style == ClockStyle.HOURS_12) {
        val h = if (hour % 12 == 0) 12 else hour % 12
        ClockText("$h:$mm", amPm.getOrNull(if (hour < 12) 0 else 1))
    } else {
        ClockText("${hour.toString().padStart(2, '0')}:$mm", null)
    }
}

private val ClockShadow = Shadow(color = Color.Black.copy(alpha = 0.55f), offset = Offset(0f, 2f), blurRadius = 8f)

@Composable
fun HomeClock(style: ClockStyle, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // Wake on each minute boundary rather than polling.
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L - now % 60_000L + 50L)
        }
    }
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val amPm = remember(style) { java.text.DateFormatSymbols(displayLocale()).amPmStrings }
    val text = formatClock(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), style, amPm)
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = text.time,
            color = JellioText,
            style = TextStyle(
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                fontFeatureSettings = "tnum",
                shadow = ClockShadow,
            ),
            modifier = Modifier.alignByBaseline(),
        )
        text.period?.let {
            Text(
                text = it,
                color = JellioTextSecondary,
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.8.sp,
                    shadow = ClockShadow,
                ),
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}
