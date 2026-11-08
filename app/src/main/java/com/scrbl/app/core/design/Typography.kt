package com.scrbl.app.core.design

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.scrbl.app.R
import com.scrbl.app.domain.model.FontFamilyOption
import com.scrbl.app.domain.model.FontScaleOption

/**
 * Bundled local font families (with safe system fallbacks) and scaled typography styles
 * per `07_VISUAL_DESIGN_SYSTEM.md` section 7:
 * 1. Handwritten (`scrbl_handwritten_regular.otf` — URW Z003 Medium Italic)
 * 2. Classic Serif (`scrbl_serif_*.ttf` — Caladea) — Default on first run
 * 3. Clean Sans (`scrbl_sans_*.ttf` — Carlito)
 */
object ScrbLTypography {

    private val BundledSerifFamily = FontFamily(
        Font(R.font.scrbl_serif_regular, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.scrbl_serif_bold, FontWeight.Bold, FontStyle.Normal),
        Font(R.font.scrbl_serif_italic, FontWeight.Normal, FontStyle.Italic)
    )

    private val BundledSansFamily = FontFamily(
        Font(R.font.scrbl_sans_regular, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.scrbl_sans_bold, FontWeight.Bold, FontStyle.Normal)
    )

    private val BundledHandwrittenFamily = FontFamily(
        Font(R.font.scrbl_handwritten_regular, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.scrbl_handwritten_regular, FontWeight.Medium, FontStyle.Italic)
    )

    fun resolveFontFamily(option: FontFamilyOption): FontFamily {
        return when (option) {
            FontFamilyOption.SERIF -> BundledSerifFamily
            FontFamilyOption.SANS -> BundledSansFamily
            FontFamilyOption.HANDWRITTEN -> BundledHandwrittenFamily
        }
    }

    /**
     * Centre-aligned text style used for both composing and displaying a Text Recall.
     */
    fun recallBodyTextStyle(
        familyOption: FontFamilyOption,
        scaleOption: FontScaleOption,
        color: androidx.compose.ui.graphics.Color
    ): TextStyle {
        val family = resolveFontFamily(familyOption)
        val baseSp = when (familyOption) {
            FontFamilyOption.HANDWRITTEN -> 23f
            FontFamilyOption.SERIF -> 20f
            FontFamilyOption.SANS -> 19f
        }
        val scaledSp = (baseSp * scaleOption.scaleFactor).sp
        return TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = scaledSp,
            lineHeight = (scaledSp.value * 1.38f).sp,
            color = color,
            textAlign = TextAlign.Center
        )
    }

    fun stackLabelTextStyle(
        familyOption: FontFamilyOption,
        scaleOption: FontScaleOption,
        color: androidx.compose.ui.graphics.Color
    ): TextStyle {
        val family = resolveFontFamily(familyOption)
        val scaledSp = (16f * scaleOption.scaleFactor.coerceIn(0.9f, 1.15f)).sp
        return TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = scaledSp,
            color = color,
            textAlign = TextAlign.Center
        )
    }

    fun metadataTextStyle(
        familyOption: FontFamilyOption,
        scaleOption: FontScaleOption,
        color: androidx.compose.ui.graphics.Color
    ): TextStyle {
        val family = resolveFontFamily(familyOption)
        // Clamp metadata scaling so date, time, and 03 / 12 index never clip on expanded pages
        val scaledSp = (13f * scaleOption.scaleFactor.coerceIn(0.9f, 1.12f)).sp
        return TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = scaledSp,
            color = color
        )
    }

    @Composable
    fun rememberMaterialTypography(
        familyOption: FontFamilyOption,
        scaleOption: FontScaleOption,
        inkColor: androidx.compose.ui.graphics.Color
    ): Typography {
        return remember(familyOption, scaleOption, inkColor) {
            val family = resolveFontFamily(familyOption)
            val s = scaleOption.scaleFactor
            Typography(
                headlineMedium = TextStyle(
                    fontFamily = family,
                    fontWeight = FontWeight.Bold,
                    fontSize = (26f * s).sp,
                    color = inkColor
                ),
                titleMedium = TextStyle(
                    fontFamily = family,
                    fontWeight = FontWeight.Bold,
                    fontSize = (18f * s).sp,
                    color = inkColor
                ),
                bodyLarge = TextStyle(
                    fontFamily = family,
                    fontWeight = FontWeight.Normal,
                    fontSize = (16f * s).sp,
                    lineHeight = (22f * s).sp,
                    color = inkColor
                ),
                bodyMedium = TextStyle(
                    fontFamily = family,
                    fontWeight = FontWeight.Normal,
                    fontSize = (14f * s).sp,
                    lineHeight = (20f * s).sp,
                    color = inkColor
                ),
                labelMedium = TextStyle(
                    fontFamily = family,
                    fontWeight = FontWeight.Normal,
                    fontSize = (12f * s.coerceAtMost(1.15f)).sp,
                    color = inkColor
                )
            )
        }
    }
}
