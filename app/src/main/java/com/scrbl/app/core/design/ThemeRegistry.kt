package com.scrbl.app.core.design

import androidx.compose.ui.graphics.Color

/**
 * Data-driven theme definition matching `design-tokens.json` and `07_VISUAL_DESIGN_SYSTEM.md`.
 *
 * Rules:
 * - V1 ships with one initial Warm Notebook theme (`warm_notebook`), while keeping the registry
 *   data-driven for future theme packs.
 * - Because only one theme is installed in V1, Settings must NOT display a pointless multi-theme selector.
 * - Addressed and unaddressed recall tones use two shades from the same hue family (`addressedTone` vs `unaddressedTone`)
 *   plus two corresponding warm paper shades (`paperAddressed` vs `paper`). Users cannot configure these independently.
 * - The home-screen widget automatically inherits the active app theme.
 */
data class ScrbLThemeTokens(
    val id: String,
    val name: String,
    val paper: Color,
    val paperAddressed: Color,
    val backSheet1: Color,
    val backSheet2: Color,
    val background: Color,
    val inkNavy: Color,
    val addressedTone: Color,
    val unaddressedTone: Color,
    val terracotta: Color,
    val sage: Color,
    val paperEdge: Color,
    val softShadow: Color,
    val dimmedScrim: Color
)

object ThemeRegistry {

    val WarmNotebook = ScrbLThemeTokens(
        id = "warm_notebook",
        name = "Warm Notebook",
        paper = Color(0xFFF4E9D5),
        paperAddressed = Color(0xFFEDE0C9),
        backSheet1 = Color(0xFFEDE1CC),
        backSheet2 = Color(0xFFD8C9B0),
        background = Color(0xFFE9DDCA),
        inkNavy = Color(0xFF29364A),
        addressedTone = Color(0xFF65748A),
        unaddressedTone = Color(0xFF29364A),
        terracotta = Color(0xFFC9786B),
        sage = Color(0xFF89917B),
        paperEdge = Color(0xFFD9CBB4),
        softShadow = Color(0x1A382D24),
        dimmedScrim = Color(0x7A29364A)
    )

    private val installedThemes = listOf(WarmNotebook)

    val hasMultipleThemes: Boolean
        get() = installedThemes.size > 1

    fun resolveTheme(themeId: String?): ScrbLThemeTokens {
        return installedThemes.firstOrNull { it.id == themeId } ?: WarmNotebook
    }
}
