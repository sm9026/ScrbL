package com.scrbl.app.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.scrbl.app.core.design.DoodleAssets
import com.scrbl.app.core.design.DoodleIconButton
import com.scrbl.app.core.design.LocalScrbLTheme
import com.scrbl.app.core.design.LocalUserPreferences
import com.scrbl.app.core.design.PaperBackground
import com.scrbl.app.core.design.PaperSheet
import com.scrbl.app.core.design.ScrbLTypography
import com.scrbl.app.core.design.ThemeRegistry
import com.scrbl.app.core.time.DateFormatOption
import com.scrbl.app.core.time.TimeFormatOption
import com.scrbl.app.domain.model.FontFamilyOption
import com.scrbl.app.domain.model.FontScaleOption
import com.scrbl.app.domain.model.PaperEdgeOption
import com.scrbl.app.domain.model.PaperTextureOption
import com.scrbl.app.domain.model.ShadowStyleOption
import com.scrbl.app.domain.model.VoiceCharacterOption
import com.scrbl.app.domain.model.WidgetSpiralOption

/**
 * Profile / Personalisation screen (`02_REQUIREMENTS_AND_RULES.md` section 10 & `04_UX_SCREEN_SPEC.md` section B):
 * - Prominent entry rows for Archive and Favourites at the top.
 * - Grouped into Appearance & Personalisation, Recall Preferences, and General.
 * - Theme Registry supports future theme packs, but because only one theme (`Warm Notebook`) is installed
 *   in V1, no pointless multi-theme selector is displayed.
 * - Reset preferences resets ONLY DataStore preference values to defaults and NEVER touches Room recalls or voice files.
 * - Explicitly excludes all forbidden settings (no editable limits, no default capture mode, no widget theme,
 *   no status-colour picker, no storage/database manager, no backup/export/import, no app lock).
 */
@Composable
fun ProfileSettingsScreen(
    onOpenArchive: () -> Unit,
    onOpenFavourites: () -> Unit,
    onOpenHelpGuide: () -> Unit,
    onOpenAbout: () -> Unit,
    onSetFontFamily: (FontFamilyOption) -> Unit,
    onSetFontScale: (FontScaleOption) -> Unit,
    onSetPaperTexture: (PaperTextureOption) -> Unit,
    onSetPaperEdge: (PaperEdgeOption) -> Unit,
    onSetStackDepth: (Int) -> Unit,
    onSetShadowStyle: (ShadowStyleOption) -> Unit,
    onSetWidgetSpiralStyle: (WidgetSpiralOption) -> Unit,
    onSetVoiceCharacter: (VoiceCharacterOption) -> Unit,
    onSetAnimationsEnabled: (Boolean) -> Unit,
    onSetTimeFormat: (TimeFormatOption) -> Unit,
    onSetDateFormat: (DateFormatOption) -> Unit,
    onSetHapticsEnabled: (Boolean) -> Unit,
    onSetSoundEffectsEnabled: (Boolean) -> Unit,
    onSetKeepScreenAwake: (Boolean) -> Unit,
    onResetPreferencesOnly: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalScrbLTheme.current
    val prefs = LocalUserPreferences.current
    var showResetConfirmationBanner by remember { mutableStateOf(false) }

    BackHandler {
        onNavigateBack()
    }

    PaperBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DoodleIconButton(
                    iconRes = DoodleAssets.BackArrow,
                    contentDescription = "Back to main notebook",
                    onClick = onNavigateBack,
                    visualDiameter = 40.dp,
                    glyphSize = 20.dp
                )
                Text(
                    text = "Notebook & Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    color = theme.inkNavy,
                    modifier = Modifier.semantics { heading() }
                )
            }

            // Prominent Archive & Favourites navigation entries
            PaperSheet(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp,
                showFoldedCorner = true,
                contentPadding = 16.dp
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsActionRow(
                        title = "Archive",
                        subtitle = "Browse year ('YY), month (MM), and day (DD/MM) stacks",
                        iconRes = DoodleAssets.Archive,
                        onClick = onOpenArchive
                    )
                    SettingsActionRow(
                        title = "Favourites",
                        subtitle = "All hearted pages across every year",
                        iconRes = DoodleAssets.HeartFilled,
                        preserveIconTint = true,
                        onClick = onOpenFavourites
                    )
                }
            }

            // Section 1: Appearance & Personalisation
            SectionHeader(title = "Appearance & Personalisation")
            PaperSheet(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp,
                contentPadding = 16.dp
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Only show theme selector if future theme packs are installed
                    if (ThemeRegistry.hasMultipleThemes) {
                        SettingsCycleRow(
                            label = "Notebook theme",
                            valueText = theme.name,
                            onCycleNext = {}
                        )
                    }

                    SettingsCycleRow(
                        label = "Typography",
                        valueText = prefs.fontFamily.displayLabel,
                        onCycleNext = {
                            val entries = FontFamilyOption.entries
                            val next = entries[(entries.indexOf(prefs.fontFamily) + 1) % entries.size]
                            onSetFontFamily(next)
                        }
                    )

                    SettingsCycleRow(
                        label = "Font size",
                        valueText = prefs.fontScale.displayLabel,
                        onCycleNext = {
                            val entries = FontScaleOption.entries
                            val next = entries[(entries.indexOf(prefs.fontScale) + 1) % entries.size]
                            onSetFontScale(next)
                        }
                    )

                    SettingsCycleRow(
                        label = "Page texture",
                        valueText = prefs.paperTexture.displayLabel,
                        onCycleNext = {
                            val entries = PaperTextureOption.entries
                            val next = entries[(entries.indexOf(prefs.paperTexture) + 1) % entries.size]
                            onSetPaperTexture(next)
                        }
                    )

                    SettingsCycleRow(
                        label = "Page edge style",
                        valueText = prefs.paperEdge.displayLabel,
                        onCycleNext = {
                            val entries = PaperEdgeOption.entries
                            val next = entries[(entries.indexOf(prefs.paperEdge) + 1) % entries.size]
                            onSetPaperEdge(next)
                        }
                    )

                    SettingsCycleRow(
                        label = "Stack depth",
                        valueText = "${prefs.stackDepth} sheets",
                        onCycleNext = {
                            val next = when (prefs.stackDepth) {
                                2 -> 3
                                3 -> 4
                                else -> 2
                            }
                            onSetStackDepth(next)
                        }
                    )

                    SettingsCycleRow(
                        label = "Page shadow",
                        valueText = prefs.shadowStyle.displayLabel,
                        onCycleNext = {
                            val entries = ShadowStyleOption.entries
                            val next = entries[(entries.indexOf(prefs.shadowStyle) + 1) % entries.size]
                            onSetShadowStyle(next)
                        }
                    )

                    SettingsCycleRow(
                        label = "Widget spiral binding",
                        valueText = prefs.widgetSpiralStyle.displayLabel,
                        onCycleNext = {
                            val entries = WidgetSpiralOption.entries
                            val next = entries[(entries.indexOf(prefs.widgetSpiralStyle) + 1) % entries.size]
                            onSetWidgetSpiralStyle(next)
                        }
                    )

                    SettingsCycleRow(
                        label = "Voice doodle character",
                        valueText = prefs.voiceCharacter.displayLabel,
                        onCycleNext = {
                            val next = if (prefs.voiceCharacter == VoiceCharacterOption.BOY) {
                                VoiceCharacterOption.GIRL
                            } else {
                                VoiceCharacterOption.BOY
                            }
                            onSetVoiceCharacter(next)
                        }
                    )

                    SettingsToggleRow(
                        label = "Paper handling animations",
                        checked = prefs.animationsEnabled,
                        onToggle = { onSetAnimationsEnabled(!prefs.animationsEnabled) }
                    )
                }
            }

            // Section 2: Recall Preferences
            SectionHeader(title = "Recall Preferences")
            PaperSheet(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp,
                contentPadding = 16.dp
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SettingsCycleRow(
                        label = "Time format",
                        valueText = prefs.timeFormat.displayLabel,
                        onCycleNext = {
                            val entries = TimeFormatOption.entries
                            val next = entries[(entries.indexOf(prefs.timeFormat) + 1) % entries.size]
                            onSetTimeFormat(next)
                        }
                    )

                    SettingsCycleRow(
                        label = "Date format",
                        valueText = prefs.dateFormat.displayLabel,
                        onCycleNext = {
                            val entries = DateFormatOption.entries
                            val next = entries[(entries.indexOf(prefs.dateFormat) + 1) % entries.size]
                            onSetDateFormat(next)
                        }
                    )
                }
            }

            // Section 3: General
            SectionHeader(title = "General")
            PaperSheet(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp,
                contentPadding = 16.dp
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SettingsToggleRow(
                        label = "Haptic feedback",
                        checked = prefs.hapticsEnabled,
                        onToggle = { onSetHapticsEnabled(!prefs.hapticsEnabled) }
                    )

                    SettingsToggleRow(
                        label = "Subtle sound effects",
                        checked = prefs.soundEffectsEnabled,
                        onToggle = { onSetSoundEffectsEnabled(!prefs.soundEffectsEnabled) }
                    )

                    SettingsToggleRow(
                        label = "Keep screen awake while recording voice",
                        checked = prefs.keepScreenAwakeDuringRecording,
                        onToggle = { onSetKeepScreenAwake(!prefs.keepScreenAwakeDuringRecording) }
                    )

                    SettingsActionRow(
                        title = "Help & Gesture Guide",
                        subtitle = "How paper stacks, double-tap, flick-away, and the widget work",
                        iconRes = DoodleAssets.CheckTick,
                        onClick = onOpenHelpGuide
                    )

                    SettingsActionRow(
                        title = "About ScrbL & Licences",
                        subtitle = "Version 1.0.0, bundled font licences, and local-only design",
                        iconRes = DoodleAssets.Settings,
                        onClick = onOpenAbout
                    )

                    SettingsActionRow(
                        title = "Reset preferences",
                        subtitle = if (showResetConfirmationBanner) {
                            "Preferences restored to defaults. Saved recalls were not touched."
                        } else {
                            "Restore default appearance & settings (never deletes recalls)"
                        },
                        iconRes = DoodleAssets.Filter,
                        onClick = {
                            onResetPreferencesOnly()
                            showResetConfirmationBanner = true
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    val theme = LocalScrbLTheme.current
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = theme.inkNavy,
        modifier = Modifier
            .padding(start = 4.dp, top = 4.dp)
            .semantics { heading() }
    )
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    iconRes: Int,
    preserveIconTint: Boolean = false,
    onClick: () -> Unit
) {
    val theme = LocalScrbLTheme.current
    val prefs = LocalUserPreferences.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { role = Role.Button }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = theme.inkNavy
            )
            Text(
                text = subtitle,
                style = ScrbLTypography.metadataTextStyle(
                    familyOption = prefs.fontFamily,
                    scaleOption = prefs.fontScale,
                    color = theme.addressedTone
                )
            )
        }
        DoodleIconButton(
            iconRes = iconRes,
            contentDescription = title,
            onClick = onClick,
            visualDiameter = 40.dp,
            glyphSize = 20.dp,
            preserveOriginalTint = preserveIconTint
        )
    }
}

@Composable
private fun SettingsCycleRow(
    label: String,
    valueText: String,
    onCycleNext: () -> Unit
) {
    val theme = LocalScrbLTheme.current
    val prefs = LocalUserPreferences.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { role = Role.Button }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCycleNext
            )
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = theme.inkNavy
            )
            Text(
                text = valueText,
                style = ScrbLTypography.metadataTextStyle(
                    familyOption = prefs.fontFamily,
                    scaleOption = prefs.fontScale,
                    color = theme.terracotta
                )
            )
        }
        DoodleIconButton(
            iconRes = DoodleAssets.Plus,
            contentDescription = "Change $label (currently $valueText)",
            onClick = onCycleNext,
            visualDiameter = 38.dp,
            glyphSize = 18.dp
        )
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    val theme = LocalScrbLTheme.current
    val prefs = LocalUserPreferences.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { role = Role.Switch }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle
            )
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = theme.inkNavy
            )
            Text(
                text = if (checked) "Enabled" else "Off",
                style = ScrbLTypography.metadataTextStyle(
                    familyOption = prefs.fontFamily,
                    scaleOption = prefs.fontScale,
                    color = if (checked) theme.terracotta else theme.addressedTone
                )
            )
        }
        DoodleIconButton(
            iconRes = DoodleAssets.CheckTick,
            contentDescription = "$label, ${if (checked) "enabled" else "disabled"}",
            onClick = onToggle,
            visualDiameter = 38.dp,
            glyphSize = 18.dp,
            isAccentFilled = checked,
            isSelected = checked
        )
    }
}
