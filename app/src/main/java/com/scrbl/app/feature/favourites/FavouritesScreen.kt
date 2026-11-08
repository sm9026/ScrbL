package com.scrbl.app.feature.favourites

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scrbl.app.core.design.DoodleAssets
import com.scrbl.app.core.design.DoodleIconButton
import com.scrbl.app.core.design.LocalScrbLTheme
import com.scrbl.app.core.design.LocalUserPreferences
import com.scrbl.app.core.design.PaperBackground
import com.scrbl.app.core.design.PaperSheet
import com.scrbl.app.core.design.PaperStackCard
import com.scrbl.app.core.design.ScrbLTypography
import com.scrbl.app.core.time.RecallDateTimeFormatter
import com.scrbl.app.domain.model.Recall

/**
 * Dedicated Favourites view (`02_REQUIREMENTS_AND_RULES.md` section 8 & `04_UX_SCREEN_SPEC.md` section I):
 * - Query/view over existing recall records (`isFavourite = true`) across all years, newest first.
 * - Never creates duplicate records or a second table.
 * - Uses the same 3-column paper-stack visual language.
 * - Tapping any favourite opens the shared Expanded Recall Page scoped to the favourites sequence.
 * - Unfavouriting a record immediately updates this view via Room's reactive `Flow`.
 */
@Composable
fun FavouritesScreen(
    favouriteRecallsNewestFirst: List<Recall>,
    onFavouriteRecallClick: (Recall) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalScrbLTheme.current
    val prefs = LocalUserPreferences.current

    BackHandler {
        onNavigateBack()
    }

    PaperBackground(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DoodleIconButton(
                    iconRes = DoodleAssets.BackArrow,
                    contentDescription = "Back to settings",
                    onClick = onNavigateBack,
                    visualDiameter = 40.dp,
                    glyphSize = 20.dp
                )
                Text(
                    text = "Favourites",
                    style = MaterialTheme.typography.headlineMedium,
                    color = theme.inkNavy,
                    modifier = Modifier.semantics { heading() }
                )
            }

            if (favouriteRecallsNewestFirst.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PaperSheet(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 320.dp)
                            .height(190.dp),
                        isAddressedTone = false,
                        cornerRadius = 14.dp,
                        showFoldedCorner = true,
                        contentPadding = 22.dp
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Tap the hand-drawn heart on any expanded recall page to keep it here.",
                                style = ScrbLTypography.recallBodyTextStyle(
                                    familyOption = prefs.fontFamily,
                                    scaleOption = prefs.fontScale,
                                    color = theme.addressedTone
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(
                        items = favouriteRecallsNewestFirst,
                        key = { "fav_${it.id}" }
                    ) { recall ->
                        val dateLabel = RecallDateTimeFormatter.formatDayStackFace(
                            capturedLocalDateIso = recall.capturedLocalDate,
                            patternPref = prefs.dateFormat
                        )
                        PaperStackCard(
                            faceLabel = dateLabel,
                            contentDescriptionLabel = "Favourite recall from $dateLabel",
                            isAddressedTone = recall.isAddressed,
                            onClick = { onFavouriteRecallClick(recall) },
                            isMonthOrYearStack = false
                        )
                    }
                }
            }
        }
    }
}
