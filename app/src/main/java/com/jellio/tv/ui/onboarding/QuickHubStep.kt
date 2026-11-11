package com.jellio.tv.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jellio.tv.data.prefs.QuickHubButton
import com.jellio.tv.data.prefs.QuickHubKeys
import com.jellio.tv.data.prefs.QuickHubPreferences
import com.jellio.tv.data.prefs.QuickHubTrigger
import com.jellio.tv.ui.i18n.formatString
import com.jellio.tv.ui.i18n.string
import com.jellio.tv.ui.settings.RecordRemoteButtonDialog
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import kotlinx.coroutines.delay

private const val GOOGLE_TV_FEATURE = "com.google.android.feature.GOOGLE_TV"

@Composable
internal fun StepQuickHubContent(
    maxBodyHeight: Dp,
    nextFocusRequester: FocusRequester,
    backFocusRequester: FocusRequester?,
) {
    val context = LocalContext.current
    val isAmazon = remember { isAmazonDevice(android.os.Build.MANUFACTURER, android.os.Build.BRAND) }
    val family = remember(isAmazon) {
        val isGoogleTv = runCatching { context.packageManager.hasSystemFeature(GOOGLE_TV_FEATURE) }.getOrDefault(false)
        deviceFamily(isAmazon, isGoogleTv)
    }

    val customCode = QuickHubPreferences.customKeyCode
    val selectedButton = preselectedButton(
        current = QuickHubPreferences.button,
        customKeyValid = QuickHubKeys.isValidCustomKey(customCode),
        isAmazon = isAmazon,
    )
    val selectedTrigger = QuickHubPreferences.trigger
    val shortPressLocked = QuickHubKeys.forcesLongPress(selectedButton, customCode)

    val firstCardFocus = remember { FocusRequester() }
    val customFocus = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    var showRecorder by remember { mutableStateOf(false) }
    var recorderWasShown by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repeat(6) {
            if (runCatching { firstCardFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(16)
        }
    }
    // The recorder is its own window; hand focus back to the card that opened it.
    LaunchedEffect(showRecorder) {
        if (showRecorder) {
            recorderWasShown = true
        } else if (recorderWasShown) {
            repeat(6) {
                if (runCatching { customFocus.requestFocus() }.isSuccess) return@LaunchedEffect
                delay(16)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxBodyHeight)
            .verticalScroll(scrollState),
    ) {
        Text(
            text = string("onboarding_step_quick_hub"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = JellioText,
        )
        Text(
            text = string("onboarding_quick_hub_sub"),
            style = MaterialTheme.typography.bodyMedium,
            color = JellioTextSecondary,
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
        )

        val features = listOf(
            Triple(Icons.Filled.GraphicEq, "quick_hub_tab_now_playing", "onboarding_quick_hub_now_playing_desc"),
            Triple(Icons.Filled.Notifications, "quick_hub_tab_notifications", "onboarding_quick_hub_notifications_desc"),
            Triple(Icons.Filled.Groups, "quick_hub_tab_group_watch", "onboarding_quick_hub_watch_party_desc"),
            Triple(Icons.Filled.People, "quick_hub_tab_friends", "onboarding_quick_hub_friends_desc"),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            features.chunked(2).forEach { pair ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                ) {
                    pair.forEach { (icon, labelKey, descKey) ->
                        FeatureChip(
                            icon = icon,
                            label = string(labelKey),
                            description = string(descKey),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = string("onboarding_quick_hub_choose_title"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = JellioText,
        )
        Text(
            text = string(family.recommendedForKey),
            style = MaterialTheme.typography.bodySmall,
            color = JellioTextSecondary,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
        ) {
            QUICK_HUB_SIMPLE_BUTTONS.forEachIndexed { index, button ->
                ChoiceCard(
                    title = string(button.labelKey),
                    description = string(button.subKey),
                    selected = selectedButton == button,
                    recommended = isRecommendedButton(button, isAmazon),
                    showReason = true,
                    onClick = { QuickHubPreferences.setButton(context, button) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(if (index == 0) Modifier.focusRequester(firstCardFocus) else Modifier),
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val customLabel = QuickHubPreferences.customKeyLabel.ifBlank {
            QuickHubPreferences.getKeyDisplayName(customCode)
        }
        val customSelected = selectedButton == QuickHubButton.CUSTOM
        ChoiceCard(
            title = string(QuickHubButton.CUSTOM.labelKey),
            description = if (customSelected) {
                formatString(QuickHubButton.CUSTOM.subKey, customLabel)
            } else {
                string("settings_quick_hub_record_btn_desc")
            },
            selected = customSelected,
            recommended = false,
            showReason = false,
            leadingIcon = Icons.Filled.SettingsRemote,
            onClick = { showRecorder = true },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(customFocus),
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = string("onboarding_quick_hub_trigger_title"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = JellioText,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
        ) {
            QuickHubTrigger.entries.forEach { trigger ->
                ChoiceCard(
                    title = string(trigger.labelKey),
                    description = string(trigger.subKey),
                    selected = selectedTrigger == trigger,
                    recommended = isRecommendedTrigger(trigger),
                    showReason = false,
                    enabled = !(trigger == QuickHubTrigger.SHORT_PRESS && shortPressLocked),
                    onClick = { QuickHubPreferences.setTrigger(context, trigger) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }
        if (shortPressLocked) {
            Text(
                text = string("settings_quick_hub_dialog_nav_note"),
                style = MaterialTheme.typography.bodySmall,
                color = JellioTextSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = string("onboarding_quick_hub_hint"),
                style = MaterialTheme.typography.bodySmall,
                color = JellioTextSecondary,
                modifier = Modifier.weight(1f),
            )
            NoShortcutAction(
                selected = selectedButton == QuickHubButton.NONE,
                onClick = { QuickHubPreferences.setButton(context, QuickHubButton.NONE) },
                downTarget = nextFocusRequester,
            )
        }
    }

    if (showRecorder) {
        Dialog(
            onDismissRequest = { showRecorder = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false),
        ) {
            RecordRemoteButtonDialog(
                onDismiss = { showRecorder = false },
                onSave = { keyCode, label, trigger ->
                    QuickHubPreferences.setCustomButton(context, keyCode, label, trigger)
                    showRecorder = false
                },
            )
        }
    }
}

@Composable
private fun FeatureChip(
    icon: ImageVector,
    label: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = JellioSecondary, modifier = Modifier.size(24.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = JellioText,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = JellioTextSecondary,
            )
        }
    }
}

@Composable
private fun ChoiceCard(
    title: String,
    description: String,
    selected: Boolean,
    recommended: Boolean,
    showReason: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) JellioSecondary.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f),
            contentColor = JellioText,
            focusedContainerColor = if (selected) JellioSecondary.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.14f),
            focusedContentColor = JellioText,
            disabledContainerColor = Color.White.copy(alpha = 0.03f),
            disabledContentColor = JellioTextSecondary.copy(alpha = 0.5f),
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                BorderStroke(
                    if (selected) 2.dp else 1.dp,
                    if (selected) JellioSecondary else Color.White.copy(alpha = 0.08f),
                ),
            ),
            focusedBorder = Border(BorderStroke(2.dp, Color.White)),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (selected) JellioSecondary else JellioText,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (selected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = JellioSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) JellioTextSecondary else JellioTextSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (recommended) {
                    Spacer(modifier = Modifier.height(6.dp))
                    RecommendedBadge()
                    if (showReason) {
                        Text(
                            text = string("onboarding_quick_hub_recommended_reason"),
                            style = MaterialTheme.typography.bodySmall,
                            color = JellioTextSecondary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendedBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(JellioSecondary.copy(alpha = 0.16f))
            .border(1.dp, JellioSecondary.copy(alpha = 0.30f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 1.dp),
    ) {
        Text(
            text = string("onboarding_quick_hub_recommended"),
            style = MaterialTheme.typography.labelSmall,
            color = JellioSecondary,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun NoShortcutAction(
    selected: Boolean,
    onClick: () -> Unit,
    downTarget: FocusRequester,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) JellioSecondary.copy(alpha = 0.16f) else Color.Transparent,
            contentColor = if (selected) JellioText else JellioTextSecondary,
            focusedContainerColor = Color.White.copy(alpha = 0.12f),
            focusedContentColor = JellioText,
        ),
        border = ClickableSurfaceDefaults.border(
            border = if (selected) Border(BorderStroke(1.dp, JellioSecondary)) else Border.None,
            focusedBorder = Border(BorderStroke(2.dp, JellioSecondary)),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.focusProperties { down = downTarget },
    ) {
        Text(
            text = string("onboarding_quick_hub_none"),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

// Summary value, e.g. "Long press Back".
@Composable
internal fun quickHubSummaryValue(): String {
    val button = QuickHubPreferences.button
    val template = quickHubSummaryTemplateKey(button, QuickHubPreferences.trigger)
    return when (button) {
        QuickHubButton.NONE -> string(template)
        QuickHubButton.CUSTOM -> formatString(
            template,
            QuickHubPreferences.customKeyLabel.ifBlank {
                QuickHubPreferences.getKeyDisplayName(QuickHubPreferences.customKeyCode)
            },
        )
        else -> formatString(template, string(shortNameKey(button) ?: button.labelKey))
    }
}
