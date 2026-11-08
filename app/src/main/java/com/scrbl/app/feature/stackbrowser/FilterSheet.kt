package com.scrbl.app.feature.stackbrowser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.scrbl.app.core.design.DoodleAssets
import com.scrbl.app.core.design.DoodleIconButton
import com.scrbl.app.core.design.LocalScrbLTheme
import com.scrbl.app.core.design.LocalUserPreferences
import com.scrbl.app.core.design.PaperSheet
import com.scrbl.app.core.design.ScrbLTypography
import com.scrbl.app.domain.model.AddressFilterMode
import com.scrbl.app.domain.model.ContentTypeFilterMode
import com.scrbl.app.domain.model.RecallFilterState

/**
 * Compact paper-styled filter sheet shown ONLY in day-stack browsing context (`02_REQUIREMENTS_AND_RULES.md` section 8):
 * - Categories: Favourite, Addressed, Unaddressed, Content type (Text / Voice / Both).
 * - Addressed-only and Unaddressed-only are mutually exclusive.
 * - Favourite is an independent predicate and combines with addressed state and content type.
 * - Main and Archive filter states persist independently.
 * - Uses circular doodle buttons so every visible button remains circular.
 */
@Composable
fun FilterSheet(
    contextTitle: String,
    filterState: RecallFilterState,
    onUpdateFilter: (RecallFilterState) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalScrbLTheme.current
    val prefs = LocalUserPreferences.current

    PaperSheet(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 380.dp)
            .semantics {
                contentDescription = "$contextTitle filter sheet"
            },
        isAddressedTone = false,
        cornerRadius = 14.dp,
        showFoldedCorner = false,
        contentPadding = 18.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = contextTitle,
                    style = ScrbLTypography.stackLabelTextStyle(
                        familyOption = prefs.fontFamily,
                        scaleOption = prefs.fontScale,
                        color = theme.inkNavy
                    )
                )
                DoodleIconButton(
                    iconRes = DoodleAssets.CheckTick,
                    contentDescription = "Close filter panel",
                    onClick = onDismiss,
                    visualDiameter = 38.dp,
                    glyphSize = 18.dp,
                    isAccentFilled = true
                )
            }

            // 1. Favourite independent toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Favourites only",
                    style = ScrbLTypography.metadataTextStyle(
                        familyOption = prefs.fontFamily,
                        scaleOption = prefs.fontScale,
                        color = theme.inkNavy
                    )
                )
                DoodleIconButton(
                    iconRes = if (filterState.favouritesOnly) {
                        DoodleAssets.HeartFilled
                    } else {
                        DoodleAssets.HeartOutline
                    },
                    contentDescription = if (filterState.favouritesOnly) {
                        "Favourites only filter active. Tap to include all."
                    } else {
                        "Favourites only filter inactive. Tap to show only favourites."
                    },
                    onClick = {
                        onUpdateFilter(filterState.copy(favouritesOnly = !filterState.favouritesOnly))
                    },
                    visualDiameter = 40.dp,
                    glyphSize = 20.dp,
                    isSelected = filterState.favouritesOnly,
                    preserveOriginalTint = filterState.favouritesOnly
                )
            }

            // 2. Addressed / Unaddressed mutually exclusive toggle
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Status (mutually exclusive)",
                    style = ScrbLTypography.metadataTextStyle(
                        familyOption = prefs.fontFamily,
                        scaleOption = prefs.fontScale,
                        color = theme.addressedTone
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AddressFilterOptionRow(
                        label = "All",
                        selected = filterState.addressFilter == AddressFilterMode.ALL,
                        onClick = {
                            onUpdateFilter(filterState.copy(addressFilter = AddressFilterMode.ALL))
                        }
                    )
                    AddressFilterOptionRow(
                        label = "Addressed",
                        selected = filterState.addressFilter == AddressFilterMode.ADDRESSED_ONLY,
                        onClick = {
                            val next = if (filterState.addressFilter == AddressFilterMode.ADDRESSED_ONLY) {
                                AddressFilterMode.ALL
                            } else {
                                AddressFilterMode.ADDRESSED_ONLY
                            }
                            onUpdateFilter(filterState.copy(addressFilter = next))
                        }
                    )
                    AddressFilterOptionRow(
                        label = "Unaddressed",
                        selected = filterState.addressFilter == AddressFilterMode.UNADDRESSED_ONLY,
                        onClick = {
                            val next = if (filterState.addressFilter == AddressFilterMode.UNADDRESSED_ONLY) {
                                AddressFilterMode.ALL
                            } else {
                                AddressFilterMode.UNADDRESSED_ONLY
                            }
                            onUpdateFilter(filterState.copy(addressFilter = next))
                        }
                    )
                }
            }

            // 3. Content Type (Both / Text / Voice)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Content type",
                    style = ScrbLTypography.metadataTextStyle(
                        familyOption = prefs.fontFamily,
                        scaleOption = prefs.fontScale,
                        color = theme.addressedTone
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        DoodleIconButton(
                            iconRes = DoodleAssets.Filter,
                            contentDescription = "Show both text and voice recalls",
                            onClick = {
                                onUpdateFilter(filterState.copy(contentTypeFilter = ContentTypeFilterMode.BOTH))
                            },
                            visualDiameter = 40.dp,
                            glyphSize = 19.dp,
                            isAccentFilled = filterState.contentTypeFilter == ContentTypeFilterMode.BOTH,
                            isSelected = filterState.contentTypeFilter == ContentTypeFilterMode.BOTH
                        )
                        Text(
                            text = "Both",
                            style = ScrbLTypography.metadataTextStyle(
                                familyOption = prefs.fontFamily,
                                scaleOption = prefs.fontScale,
                                color = theme.inkNavy
                            )
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        DoodleIconButton(
                            iconRes = DoodleAssets.Keyboard,
                            contentDescription = "Show text recalls only",
                            onClick = {
                                onUpdateFilter(filterState.copy(contentTypeFilter = ContentTypeFilterMode.TEXT_ONLY))
                            },
                            visualDiameter = 40.dp,
                            glyphSize = 19.dp,
                            isAccentFilled = filterState.contentTypeFilter == ContentTypeFilterMode.TEXT_ONLY,
                            isSelected = filterState.contentTypeFilter == ContentTypeFilterMode.TEXT_ONLY
                        )
                        Text(
                            text = "Text",
                            style = ScrbLTypography.metadataTextStyle(
                                familyOption = prefs.fontFamily,
                                scaleOption = prefs.fontScale,
                                color = theme.inkNavy
                            )
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        DoodleIconButton(
                            iconRes = DoodleAssets.Microphone,
                            contentDescription = "Show voice recalls only",
                            onClick = {
                                onUpdateFilter(filterState.copy(contentTypeFilter = ContentTypeFilterMode.VOICE_ONLY))
                            },
                            visualDiameter = 40.dp,
                            glyphSize = 19.dp,
                            isAccentFilled = filterState.contentTypeFilter == ContentTypeFilterMode.VOICE_ONLY,
                            isSelected = filterState.contentTypeFilter == ContentTypeFilterMode.VOICE_ONLY
                        )
                        Text(
                            text = "Voice",
                            style = ScrbLTypography.metadataTextStyle(
                                familyOption = prefs.fontFamily,
                                scaleOption = prefs.fontScale,
                                color = theme.inkNavy
                            )
                        )
                    }
                }
            }

            if (filterState.isActive) {
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Tap selected icons again to reset to unfiltered",
                        style = ScrbLTypography.metadataTextStyle(
                            familyOption = prefs.fontFamily,
                            scaleOption = prefs.fontScale,
                            color = theme.addressedTone
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun AddressFilterOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalScrbLTheme.current
    val prefs = LocalUserPreferences.current

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        DoodleIconButton(
            iconRes = DoodleAssets.CheckTick,
            contentDescription = "Filter by $label",
            onClick = onClick,
            visualDiameter = 38.dp,
            glyphSize = 18.dp,
            isAccentFilled = selected,
            isSelected = selected
        )
        Text(
            text = label,
            style = ScrbLTypography.metadataTextStyle(
                familyOption = prefs.fontFamily,
                scaleOption = prefs.fontScale,
                color = if (selected) theme.inkNavy else theme.addressedTone
            )
        )
    }
}
