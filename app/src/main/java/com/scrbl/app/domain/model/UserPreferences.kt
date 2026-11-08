package com.scrbl.app.domain.model

/**
 * User preferences stored in Preferences DataStore (09_DATA_MODEL_AND_STORAGE.md).
 * Note: Fixed product rules (25-word cap, 37-second cap, 19-widget cap, addressed/unaddressed shades,
 * widget theme inheritance, capture mode availability) are NOT user-configurable and are intentionally excluded.
 */
data class UserPreferences(
    val themeId: String = DEFAULT_THEME_ID,
    val fontFamily: FontFamilyOption = FontFamilyOption.SERIF,
    val fontScale: FontScaleOption = FontScaleOption.MEDIUM,
    val paperTexture: PaperTextureOption = PaperTextureOption.SUBTLE_GRAIN,
    val paperEdge: PaperEdgeOption = PaperEdgeOption.SOFT_HANDDRAWN,
    val stackDepth: Int = 3, // 2, 3, or 4 visible layers
    val shadowStyle: ShadowStyleOption = ShadowStyleOption.SUBTLE,
    val widgetSpiralStyle: WidgetSpiralOption = WidgetSpiralOption.STANDARD,
    val voiceCharacter: VoiceCharacterOption = VoiceCharacterOption.BOY,
    val animationsEnabled: Boolean = true,
    val timeFormat: com.scrbl.app.core.time.TimeFormatOption = com.scrbl.app.core.time.TimeFormatOption.SYSTEM,
    val dateFormat: com.scrbl.app.core.time.DateFormatOption = com.scrbl.app.core.time.DateFormatOption.DD_MM,
    val hapticsEnabled: Boolean = true,
    val soundEffectsEnabled: Boolean = false,
    val keepScreenAwakeDuringRecording: Boolean = false,
    val mainFilterState: RecallFilterState = RecallFilterState(),
    val archiveFilterState: RecallFilterState = RecallFilterState()
) {
    companion object {
        const val DEFAULT_THEME_ID = "warm_notebook"
    }
}

enum class FontFamilyOption(val storageKey: String, val displayLabel: String) {
    SERIF("serif", "Classic Serif"),
    HANDWRITTEN("handwritten", "Handwritten"),
    SANS("sans", "Clean Sans");

    companion object {
        fun fromKey(key: String?): FontFamilyOption =
            entries.firstOrNull { it.storageKey == key } ?: SERIF
    }
}

enum class FontScaleOption(val storageKey: String, val scaleFactor: Float, val displayLabel: String) {
    SMALL("small", 0.90f, "Compact"),
    MEDIUM("medium", 1.00f, "Standard"),
    LARGE("large", 1.15f, "Generous"),
    EXTRA_LARGE("xlarge", 1.30f, "Large print");

    companion object {
        fun fromKey(key: String?): FontScaleOption =
            entries.firstOrNull { it.storageKey == key } ?: MEDIUM
    }
}

enum class PaperTextureOption(val storageKey: String, val displayLabel: String) {
    SUBTLE_GRAIN("subtle_grain", "Subtle paper grain"),
    PLAIN("plain", "Plain warm ivory"),
    RULED("ruled", "Faint notebook lines");

    companion object {
        fun fromKey(key: String?): PaperTextureOption =
            entries.firstOrNull { it.storageKey == key } ?: SUBTLE_GRAIN
    }
}

enum class PaperEdgeOption(val storageKey: String, val displayLabel: String) {
    SOFT_HANDDRAWN("soft_handdrawn", "Soft hand-drawn edge"),
    CLEAN("clean", "Crisp notebook cut");

    companion object {
        fun fromKey(key: String?): PaperEdgeOption =
            entries.firstOrNull { it.storageKey == key } ?: SOFT_HANDDRAWN
    }
}

enum class ShadowStyleOption(val storageKey: String, val displayLabel: String) {
    SUBTLE("subtle", "Subtle elevation"),
    SOFT("soft", "Soft lifted shadow");

    companion object {
        fun fromKey(key: String?): ShadowStyleOption =
            entries.firstOrNull { it.storageKey == key } ?: SUBTLE
    }
}

enum class WidgetSpiralOption(val storageKey: String, val displayLabel: String) {
    STANDARD("standard", "Classic wire spiral"),
    SUBTLE("subtle", "Restrained low-contrast spiral");

    companion object {
        fun fromKey(key: String?): WidgetSpiralOption =
            entries.firstOrNull { it.storageKey == key } ?: STANDARD
    }
}

enum class VoiceCharacterOption(val storageKey: String, val displayLabel: String) {
    BOY("boy", "Notebook Boy Sketch"),
    GIRL("girl", "Notebook Girl Sketch");

    companion object {
        fun fromKey(key: String?): VoiceCharacterOption =
            entries.firstOrNull { it.storageKey == key } ?: BOY
    }
}
