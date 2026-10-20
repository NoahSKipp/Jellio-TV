package com.jellio.tv.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.jellio.tv.data.model.LANGUAGE_OPTIONS
import com.jellio.tv.data.model.LanguageOption
import com.jellio.tv.data.model.languageName
import com.jellio.tv.data.prefs.AppLanguage
import com.jellio.tv.data.prefs.DisplayPreferences
import com.jellio.tv.ui.i18n.formatString
import com.jellio.tv.ui.i18n.string
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioBorder
import com.jellio.tv.ui.theme.JellioRarityRare
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary

private enum class OnboardingStep {
    LANGUAGE,
    PLAYBACK,
    OVERSCAN,
    SUMMARY,
}

@Composable
fun OnboardingOverlay(
    userId: String,
    initialAudioLang: String? = null,
    initialSubtitleLang: String? = null,
    onSavePreferences: (audio: String?, subtitle: String?) -> Unit,
    onComplete: () -> Unit,
) {
    val context = LocalContext.current

    // Detect if the user previously set up audio/subtitle preferences on Web
    val hasSharedAccountSettings = !initialAudioLang.isNullOrBlank() || (!initialSubtitleLang.isNullOrBlank() && initialSubtitleLang != "off")

    // Determine the list of relevant steps: if Web set it up, skip audio/subtitle and only show TV-specific settings
    val steps = remember(hasSharedAccountSettings) {
        if (hasSharedAccountSettings) {
            listOf(OnboardingStep.LANGUAGE, OnboardingStep.OVERSCAN, OnboardingStep.SUMMARY)
        } else {
            listOf(OnboardingStep.LANGUAGE, OnboardingStep.PLAYBACK, OnboardingStep.OVERSCAN, OnboardingStep.SUMMARY)
        }
    }

    var stepIndex by remember { mutableIntStateOf(0) }
    val currentStep = steps.getOrElse(stepIndex) { OnboardingStep.SUMMARY }

    var selectedLang by remember { mutableStateOf(DisplayPreferences.displayLanguage) }
    var currentOverscan by remember { mutableIntStateOf(DisplayPreferences.overscanPercent) }
    var selectedAudio by remember { mutableStateOf(initialAudioLang) }
    var selectedSubtitle by remember { mutableStateOf<String?>(initialSubtitleLang ?: "off") }

    var choosingField by remember { mutableStateOf<String?>(null) }
    var activeHelpDialog by remember { mutableStateOf<Pair<String, String>?>(null) }

    fun finishWizard() {
        DisplayPreferences.setDisplayLanguage(context, selectedLang)
        DisplayPreferences.setOverscan(context, currentOverscan)
        onSavePreferences(selectedAudio, selectedSubtitle)
        OnboardingPrefs.markOnboardingComplete(context, userId)
        onComplete()
    }

    Dialog(
        onDismissRequest = { finishWizard() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
        ),
    ) {
        BackHandler {
            if (stepIndex > 0) {
                stepIndex--
            } else {
                finishWizard()
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.88f))
                .focusProperties { onExit = { FocusRequester.Cancel } },
            contentAlignment = Alignment.Center,
        ) {
            // Draw alignment brackets for Overscan step
            if (currentStep == OnboardingStep.OVERSCAN) {
                val insetX = maxWidth * (currentOverscan / 100f) / 2f
                val insetY = maxHeight * (currentOverscan / 100f) / 2f
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = insetX, vertical = insetY),
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 3.dp.toPx()
                        val armLength = 48.dp.toPx()
                        val color = Color.White

                        drawRect(
                            color = color.copy(alpha = 0.40f),
                            style = Stroke(width = 1.5.dp.toPx()),
                        )

                        // Top-Left corner bracket
                        drawPath(
                            path = Path().apply {
                                moveTo(0f, armLength)
                                lineTo(0f, 0f)
                                lineTo(armLength, 0f)
                            },
                            color = color,
                            style = Stroke(width = strokeWidth),
                        )
                        // Top-Right corner bracket
                        drawPath(
                            path = Path().apply {
                                moveTo(size.width - armLength, 0f)
                                lineTo(size.width, 0f)
                                lineTo(size.width, armLength)
                            },
                            color = color,
                            style = Stroke(width = strokeWidth),
                        )
                        // Bottom-Left corner bracket
                        drawPath(
                            path = Path().apply {
                                moveTo(0f, size.height - armLength)
                                lineTo(0f, size.height)
                                lineTo(armLength, size.height)
                            },
                            color = color,
                            style = Stroke(width = strokeWidth),
                        )
                        // Bottom-Right corner bracket
                        drawPath(
                            path = Path().apply {
                                moveTo(size.width - armLength, size.height)
                                lineTo(size.width, size.height)
                                lineTo(size.width, size.height - armLength)
                            },
                            color = color,
                            style = Stroke(width = strokeWidth),
                        )
                    }
                }
            }

            // Main Card container
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(JellioBgElevated)
                    .border(1.dp, JellioBorder, RoundedCornerShape(24.dp))
                    .padding(horizontal = 28.dp, vertical = 20.dp),
            ) {
                // Header: Logo Mark, Step Counter, and Skip button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(listOf(JellioRarityRare, JellioSecondary))
                                ),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Tv,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Column {
                            Text(
                                text = "Jellio TV",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = JellioText,
                            )
                            Text(
                                text = formatString("onboarding_step_counter", stepIndex + 1, steps.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = JellioTextSecondary,
                            )
                        }
                    }

                    Surface(
                        onClick = { finishWizard() },
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.Transparent,
                            contentColor = JellioTextSecondary,
                            focusedContainerColor = Color.White.copy(alpha = 0.12f),
                            focusedContentColor = JellioText,
                        ),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(BorderStroke(2.dp, JellioSecondary)),
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    ) {
                        Text(
                            text = string("onboarding_skip"),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Pill Progress Bar
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    steps.indices.forEach { idx ->
                        val isActive = idx <= stepIndex
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (isActive) JellioSecondary else Color.White.copy(alpha = 0.15f)),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Step Body
                Box(modifier = Modifier.fillMaxWidth()) {
                    when (currentStep) {
                        OnboardingStep.LANGUAGE -> {
                            StepLanguageContent(
                                selectedLang = selectedLang,
                                onSelectLang = {
                                    selectedLang = it
                                    DisplayPreferences.setDisplayLanguage(context, it)
                                },
                            )
                        }
                        OnboardingStep.PLAYBACK -> {
                            StepPlaybackContent(
                                selectedAudio = selectedAudio,
                                selectedSubtitle = selectedSubtitle,
                                onOpenAudioPicker = { choosingField = "audio" },
                                onOpenSubtitlePicker = { choosingField = "subtitle" },
                                onShowHelp = { title, desc -> activeHelpDialog = title to desc },
                            )
                        }
                        OnboardingStep.OVERSCAN -> {
                            StepOverscanContent(
                                currentPercent = currentOverscan,
                                onChangePercent = { newPercent ->
                                    currentOverscan = newPercent.coerceIn(
                                        DisplayPreferences.MIN_PERCENT,
                                        DisplayPreferences.MAX_PERCENT
                                    )
                                    DisplayPreferences.setOverscan(context, currentOverscan)
                                },
                            )
                        }
                        OnboardingStep.SUMMARY -> {
                            StepSummaryContent(
                                displayLang = selectedLang,
                                overscan = currentOverscan,
                                audioLang = selectedAudio,
                                subtitleLang = selectedSubtitle,
                                hasSharedAccountSettings = hasSharedAccountSettings,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Footer Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (stepIndex > 0) {
                        Surface(
                            onClick = { stepIndex-- },
                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color.Transparent,
                                contentColor = JellioTextSecondary,
                                focusedContainerColor = Color.White.copy(alpha = 0.12f),
                                focusedContentColor = JellioText,
                            ),
                            border = ClickableSurfaceDefaults.border(
                                border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.20f))),
                                focusedBorder = Border(BorderStroke(2.dp, JellioSecondary)),
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        ) {
                            Text(
                                text = string("onboarding_back"),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    val isLastStep = stepIndex == steps.size - 1
                    val nextFocusRequester = remember { FocusRequester() }
                    LaunchedEffect(stepIndex) {
                        runCatching { nextFocusRequester.requestFocus() }
                    }

                    Surface(
                        onClick = {
                            if (isLastStep) {
                                finishWizard()
                            } else {
                                stepIndex++
                            }
                        },
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = JellioSecondary,
                            contentColor = Color.Black,
                            focusedContainerColor = Color.White,
                            focusedContentColor = Color.Black,
                        ),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(BorderStroke(2.dp, Color.White)),
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
                        modifier = Modifier.focusRequester(nextFocusRequester),
                    ) {
                        Text(
                            text = if (isLastStep) string("onboarding_finish") else string("onboarding_next"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 26.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        // Sub-dialog: Audio or Subtitle Language selection
        if (choosingField != null) {
            val isAudio = choosingField == "audio"
            val title = if (isAudio) string("settings_preferred_audio") else string("settings_preferred_subtitle")
            val currentCode = if (isAudio) selectedAudio else selectedSubtitle
            TvLanguagePickerModal(
                title = title,
                selectedCode = currentCode,
                isSubtitle = !isAudio,
                onSelect = { code ->
                    if (isAudio) selectedAudio = code else selectedSubtitle = code
                    choosingField = null
                },
                onDismiss = { choosingField = null },
            )
        }

        // Sub-dialog: Help explanation tooltip
        activeHelpDialog?.let { (helpTitle, helpDesc) ->
            TvHelpExplanationModal(
                title = helpTitle,
                description = helpDesc,
                onDismiss = { activeHelpDialog = null },
            )
        }
    }
}

@Composable
private fun StepLanguageContent(
    selectedLang: AppLanguage,
    onSelectLang: (AppLanguage) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = string("onboarding_welcome_title"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = JellioText,
        )
        Text(
            text = string("onboarding_welcome_sub"),
            style = MaterialTheme.typography.bodyMedium,
            color = JellioTextSecondary,
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
        )

        val languages = AppLanguage.entries
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            languages.chunked(2).forEach { rowPair ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    rowPair.forEach { lang ->
                        val isSelected = lang == selectedLang
                        Surface(
                            onClick = { onSelectLang(lang) },
                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (isSelected) JellioSecondary.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f),
                                contentColor = JellioText,
                                focusedContainerColor = if (isSelected) JellioSecondary.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.14f),
                                focusedContentColor = JellioText,
                            ),
                            border = ClickableSurfaceDefaults.border(
                                border = Border(
                                    BorderStroke(
                                        if (isSelected) 2.dp else 1.dp,
                                        if (isSelected) JellioSecondary else Color.White.copy(alpha = 0.08f)
                                    ),
                                ),
                                focusedBorder = Border(BorderStroke(2.dp, Color.White)),
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                            modifier = Modifier.weight(1f),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) JellioSecondary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f)),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Translate,
                                        contentDescription = null,
                                        tint = if (isSelected) JellioSecondary else JellioText,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                Column {
                                    Text(
                                        text = lang.nativeName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = JellioText,
                                    )
                                    Text(
                                        text = lang.label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = JellioTextSecondary,
                                    )
                                }
                            }
                        }
                    }
                    if (rowPair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun StepPlaybackContent(
    selectedAudio: String?,
    selectedSubtitle: String?,
    onOpenAudioPicker: () -> Unit,
    onOpenSubtitlePicker: () -> Unit,
    onShowHelp: (title: String, desc: String) -> Unit,
) {
    val audioHelpTitle = string("onboarding_audio_help_title")
    val audioHelpDesc = string("onboarding_audio_help_desc")
    val subHelpTitle = string("onboarding_subtitle_help_title")
    val subHelpDesc = string("onboarding_subtitle_help_desc")

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = string("onboarding_step_playback"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = JellioText,
        )
        Text(
            text = string("onboarding_playback_sub"),
            style = MaterialTheme.typography.bodyMedium,
            color = JellioTextSecondary,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            PlaybackPreferenceRow(
                icon = Icons.Filled.Audiotrack,
                label = string("settings_preferred_audio"),
                currentValue = selectedAudio?.let { languageName(it) } ?: string("settings_no_preference"),
                onClick = onOpenAudioPicker,
                onHelpClick = { onShowHelp(audioHelpTitle, audioHelpDesc) },
            )

            PlaybackPreferenceRow(
                icon = Icons.Filled.Subtitles,
                label = string("settings_preferred_subtitle"),
                currentValue = when (selectedSubtitle) {
                    "off" -> string("settings_subtitles_off")
                    null, "" -> string("settings_no_preference")
                    else -> languageName(selectedSubtitle)
                },
                onClick = onOpenSubtitlePicker,
                onHelpClick = { onShowHelp(subHelpTitle, subHelpDesc) },
            )
        }
    }
}

@Composable
private fun PlaybackPreferenceRow(
    icon: ImageVector,
    label: String,
    currentValue: String,
    onClick: () -> Unit,
    onHelpClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Surface(
            onClick = onClick,
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(14.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.05f),
                contentColor = JellioText,
                focusedContainerColor = Color.White.copy(alpha = 0.14f),
                focusedContentColor = JellioText,
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))),
                focusedBorder = Border(BorderStroke(2.dp, JellioSecondary)),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
            modifier = Modifier.weight(1f),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = JellioSecondary, modifier = Modifier.size(22.dp))
                    Column {
                        Text(text = label, style = MaterialTheme.typography.bodySmall, color = JellioTextSecondary)
                        Text(text = currentValue, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = JellioText)
                    }
                }
                Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = null, tint = JellioTextSecondary, modifier = Modifier.size(22.dp))
            }
        }

        Surface(
            onClick = onHelpClick,
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(14.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.05f),
                contentColor = JellioTextSecondary,
                focusedContainerColor = Color.White.copy(alpha = 0.14f),
                focusedContentColor = Color.White,
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))),
                focusedBorder = Border(BorderStroke(2.dp, JellioSecondary)),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            modifier = Modifier.size(46.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(imageVector = Icons.Filled.HelpOutline, contentDescription = "Help", modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun StepOverscanContent(
    currentPercent: Int,
    onChangePercent: (Int) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = string("onboarding_step_overscan"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = JellioText,
        )
        Text(
            text = string("onboarding_overscan_desc"),
            style = MaterialTheme.typography.bodyMedium,
            color = JellioTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp, bottom = 14.dp),
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .padding(horizontal = 28.dp, vertical = 12.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (currentPercent == 0) string("onboarding_overscan_fullscreen") else formatString("onboarding_overscan_inset", currentPercent),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = JellioText,
                )
                Text(
                    text = "Margin: $currentPercent%",
                    style = MaterialTheme.typography.bodySmall,
                    color = JellioTextSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = { onChangePercent(currentPercent - 1) },
                enabled = currentPercent > DisplayPreferences.MIN_PERCENT,
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.08f),
                    contentColor = JellioText,
                    focusedContainerColor = Color.White.copy(alpha = 0.20f),
                    focusedContentColor = JellioText,
                ),
                border = ClickableSurfaceDefaults.border(
                    focusedBorder = Border(BorderStroke(2.dp, JellioSecondary)),
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            ) {
                Text(
                    text = "- Less Inset",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 9.dp),
                )
            }

            Surface(
                onClick = { onChangePercent(currentPercent + 1) },
                enabled = currentPercent < DisplayPreferences.MAX_PERCENT,
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.08f),
                    contentColor = JellioText,
                    focusedContainerColor = Color.White.copy(alpha = 0.20f),
                    focusedContentColor = JellioText,
                ),
                border = ClickableSurfaceDefaults.border(
                    focusedBorder = Border(BorderStroke(2.dp, JellioSecondary)),
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            ) {
                Text(
                    text = "+ More Inset",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 9.dp),
                )
            }
        }
    }
}

@Composable
private fun StepSummaryContent(
    displayLang: AppLanguage,
    overscan: Int,
    audioLang: String?,
    subtitleLang: String?,
    hasSharedAccountSettings: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = string("onboarding_ready_title"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = JellioText,
        )
        Text(
            text = string("onboarding_ready_desc"),
            style = MaterialTheme.typography.bodyMedium,
            color = JellioTextSecondary,
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            SummaryItemRow(
                icon = Icons.Filled.Translate,
                label = string("settings_display_language"),
                value = displayLang.nativeName,
            )

            SummaryItemRow(
                icon = Icons.Filled.Tv,
                label = string("onboarding_step_overscan"),
                value = if (overscan == 0) string("onboarding_overscan_fullscreen") else formatString("onboarding_overscan_inset", overscan),
            )

            SummaryItemRow(
                icon = Icons.Filled.Audiotrack,
                label = string("settings_preferred_audio"),
                value = audioLang?.let { languageName(it) } ?: string("settings_no_preference"),
                badge = if (hasSharedAccountSettings) string("onboarding_synced_badge") else null,
            )

            SummaryItemRow(
                icon = Icons.Filled.Subtitles,
                label = string("settings_preferred_subtitle"),
                value = when (subtitleLang) {
                    "off" -> string("settings_subtitles_off")
                    null, "" -> string("settings_no_preference")
                    else -> languageName(subtitleLang)
                },
                badge = if (hasSharedAccountSettings) string("onboarding_synced_badge") else null,
            )
        }
    }
}

@Composable
private fun SummaryItemRow(
    icon: ImageVector,
    label: String,
    value: String,
    badge: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = JellioSecondary, modifier = Modifier.size(20.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = label, style = MaterialTheme.typography.bodySmall, color = JellioTextSecondary)
                    if (badge != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(JellioSecondary.copy(alpha = 0.16f))
                                .border(1.dp, JellioSecondary.copy(alpha = 0.30f), RoundedCornerShape(999.dp))
                                .padding(horizontal = 8.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = JellioSecondary,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
                Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = JellioText)
            }
        }
    }
}

@Composable
private fun TvLanguagePickerModal(
    title: String,
    selectedCode: String?,
    isSubtitle: Boolean = false,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                modifier = Modifier
                    .width(360.dp)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                    .background(JellioBgElevated)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {},
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 40.dp, bottom = 32.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = JellioText,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )

                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        if (isSubtitle) {
                            item {
                                LanguagePickerItem(
                                    label = string("settings_subtitles_off"),
                                    isSelected = selectedCode == "off" || selectedCode.isNullOrBlank(),
                                    onClick = { onSelect("off") },
                                )
                            }
                        }
                        item {
                            LanguagePickerItem(
                                label = string("settings_no_preference"),
                                isSelected = if (isSubtitle) selectedCode == "" else selectedCode == null,
                                onClick = { onSelect(if (isSubtitle) "" else null) },
                            )
                        }
                        items(LANGUAGE_OPTIONS) { option: LanguageOption ->
                            LanguagePickerItem(
                                label = option.name,
                                isSelected = selectedCode == option.code,
                                onClick = { onSelect(option.code) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguagePickerItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) Color.White.copy(alpha = 0.16f) else Color.Transparent,
            contentColor = JellioText,
            focusedContainerColor = Color.White.copy(alpha = 0.24f),
            focusedContentColor = JellioText,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, JellioSecondary)),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun TvHelpExplanationModal(
    title: String,
    description: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.70f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(480.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(JellioBgElevated)
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {},
                    )
                    .padding(28.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(imageVector = Icons.Filled.HelpOutline, contentDescription = null, tint = JellioSecondary, modifier = Modifier.size(24.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = JellioText,
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = JellioTextSecondary,
                        lineHeight = 22.sp,
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    val closeFocusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) { closeFocusRequester.requestFocus() }

                    Surface(
                        onClick = onDismiss,
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = JellioSecondary,
                            contentColor = Color.Black,
                            focusedContainerColor = Color.White,
                            focusedContentColor = Color.Black,
                        ),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(BorderStroke(2.dp, Color.White)),
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
                        modifier = Modifier
                            .align(Alignment.End)
                            .focusRequester(closeFocusRequester),
                    ) {
                        Text(
                            text = string("action_done"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 9.dp),
                        )
                    }
                }
            }
        }
    }
}
