package com.scrbl.app.feature.help

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.scrbl.app.core.design.DoodleAssets
import com.scrbl.app.core.design.DoodleIconButton
import com.scrbl.app.core.design.LocalScrbLTheme
import com.scrbl.app.core.design.LocalUserPreferences
import com.scrbl.app.core.design.PaperBackground
import com.scrbl.app.core.design.PaperSheet
import com.scrbl.app.core.design.ScrbLTypography

/**
 * Help / Gesture Guide and About / Third-Party Licences screens (`02_REQUIREMENTS_AND_RULES.md` section 10).
 */
@Composable
fun HelpGestureGuideScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalScrbLTheme.current

    BackHandler { onNavigateBack() }

    PaperBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                    text = "Help & Gesture Guide",
                    style = MaterialTheme.typography.headlineMedium,
                    color = theme.inkNavy,
                    modifier = Modifier.semantics { heading() }
                )
            }

            GuideCard(
                title = "Catching a Thought",
                body = "• Tap the circular + button to lift a blank floating paper sheet.\n" +
                    "• Both Keyboard (up to 25 words) and Microphone (up to 37 seconds) are always ready.\n" +
                    "• In Text mode, your words stay centre-aligned. Tap the top-right tick to commit immediately.\n" +
                    "• In Voice mode, recording begins immediately. Tap the circular stop icon to commit, or let it auto-save at 37 seconds.\n" +
                    "• Flick the floating page away (or press Back) to discard an unfinished thought. There are no drafts."
            )

            GuideCard(
                title = "Reading & Annotating Pages",
                body = "• Saved thoughts are permanent: they cannot be edited or deleted.\n" +
                    "• Swipe up on an expanded recall page for older thoughts; swipe down for newer thoughts.\n" +
                    "• Double-tap the expanded page (or use the TalkBack action) to toggle Addressed / Unaddressed tone.\n" +
                    "• Tap the top-right hand-drawn heart to toggle Favourite.\n" +
                    "• On a voice recall, tap the doodle character in the centre to play or pause audio."
            )

            GuideCard(
                title = "Paper Stacks & Archive",
                body = "• The Main screen displays the current calendar year in a 3-column grid.\n" +
                    "• The active month shows day stacks (DD/MM); completed months collapse into month stacks (MM).\n" +
                    "• Tap a month stack (MM) to deal its day stacks into the grid; tap the flat MM page to fold them back.\n" +
                    "• Visit Archive from Settings to browse Year ('YY) → Month (MM) → Day (DD/MM) stacks."
            )

            GuideCard(
                title = "Home-Screen Notepad Widget",
                body = "• Add the ScrbL Notepad widget from your phone launcher.\n" +
                    "• Shows up to your 19 most recent recalls (older thoughts remain safely in your archive).\n" +
                    "• Flick up for older pages; flick down for newer pages.\n" +
                    "• Tap the front page to open that exact recall directly in expanded view (with a + button for quick capture)."
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun AboutLicencesScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalScrbLTheme.current

    BackHandler { onNavigateBack() }

    PaperBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                    text = "About ScrbL",
                    style = MaterialTheme.typography.headlineMedium,
                    color = theme.inkNavy,
                    modifier = Modifier.semantics { heading() }
                )
            }

            GuideCard(
                title = "ScrbL — Version 1.0.0",
                body = "A small, private, paper-like place to catch fleeting thoughts as short text or voice and revisit them later, one physical-feeling page at a time.\n\n" +
                    "• 100% Local-First: No accounts, no cloud sync, no analytics, and no INTERNET permission.\n" +
                    "• Permanent by Design: Saved recalls and their original capture-local timestamps are immutable."
            )

            GuideCard(
                title = "Bundled Font & Artwork Licences",
                body = "1. Caladea (Classic Serif — scrbl_serif_*.ttf)\n" +
                    "Copyright 2012 Carolina Giovagnoli, Andres Torresi, Huerta Tipográfica. Licensed under the SIL Open Font License v1.1 (OFL-1.1).\n\n" +
                    "2. Carlito (Clean Sans — scrbl_sans_*.ttf)\n" +
                    "Copyright 2013 The Carlito Project Authors. Licensed under the SIL Open Font License v1.1 (OFL-1.1).\n\n" +
                    "3. URW Z003 Medium Italic (Handwritten — scrbl_handwritten_regular.otf)\n" +
                    "Copyright 2015 URW Software / (URW)++ Design & Development. Licensed under GNU AGPL v3.0 with the Font Embedding Exception.\n\n" +
                    "4. ScrbL Doodle Vector Family & Voice Sketch Characters\n" +
                    "Original first-party vector artwork created for ScrbL. Full licence text is bundled in assets/THIRD_PARTY_NOTICES.md."
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun GuideCard(
    title: String,
    body: String
) {
    val theme = LocalScrbLTheme.current
    val prefs = LocalUserPreferences.current

    PaperSheet(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 14.dp,
        showFoldedCorner = true,
        contentPadding = 18.dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = theme.inkNavy
            )
            Text(
                text = body,
                style = ScrbLTypography.metadataTextStyle(
                    familyOption = prefs.fontFamily,
                    scaleOption = prefs.fontScale,
                    color = theme.inkNavy
                )
            )
        }
    }
}
