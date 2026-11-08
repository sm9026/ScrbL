package com.scrbl.app.core.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.scrbl.app.domain.model.UserPreferences

val LocalScrbLTheme = staticCompositionLocalOf { ThemeRegistry.WarmNotebook }
val LocalUserPreferences = staticCompositionLocalOf { UserPreferences() }

@Composable
fun ScrbLTheme(
    preferences: UserPreferences = UserPreferences(),
    content: @Composable () -> Unit
) {
    val tokens = ThemeRegistry.resolveTheme(preferences.themeId)
    val typography = ScrbLTypography.rememberMaterialTypography(
        familyOption = preferences.fontFamily,
        scaleOption = preferences.fontScale,
        inkColor = tokens.inkNavy
    )
    val colorScheme = lightColorScheme(
        primary = tokens.inkNavy,
        onPrimary = tokens.paper,
        secondary = tokens.terracotta,
        onSecondary = tokens.paper,
        tertiary = tokens.sage,
        background = tokens.background,
        onBackground = tokens.inkNavy,
        surface = tokens.paper,
        onSurface = tokens.inkNavy,
        outline = tokens.paperEdge
    )

    CompositionLocalProvider(
        LocalScrbLTheme provides tokens,
        LocalUserPreferences provides preferences
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
