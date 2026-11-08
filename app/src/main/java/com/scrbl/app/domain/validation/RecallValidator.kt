package com.scrbl.app.domain.validation

import com.scrbl.app.core.result.AppResult
import com.scrbl.app.core.result.FailureReason

/**
 * Enforces ScrbL's strict product rules for text and voice recalls (02_REQUIREMENTS_AND_RULES.md):
 * - Text recall maximum: 25 words.
 * - Word-count contract: normalize line breaks and repeated whitespace for counting,
 *   but preserve natural line breaks in displayed content. Count non-empty whitespace-separated tokens.
 *   Punctuation attached to a token does not create another word. A hyphenated compound without spaces
 *   counts as one token. An emoji separated by spaces counts as a token.
 * - Do not silently truncate text. Prevent insertion beyond 25 words and validate in domain before save.
 * - Voice recall maximum: 37,000 ms (37 seconds).
 * - Optional voice title maximum: 80 characters.
 */
object RecallValidator {

    const val MAX_TEXT_WORDS: Int = 25
    const val MAX_TEXT_CHARS: Int = 2000
    const val MAX_VOICE_DURATION_MS: Long = 37_000L
    const val MAX_VOICE_TITLE_CHARS: Int = 80

    private val WHITESPACE_SPLIT_REGEX = Regex("\\s+")

    /**
     * Counts non-empty whitespace-separated tokens per 02_REQUIREMENTS_AND_RULES.md.
     */
    fun countWords(rawText: String): Int {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return 0
        return trimmed.split(WHITESPACE_SPLIT_REGEX).count { it.isNotEmpty() }
    }

    /**
     * Normalizes outer whitespace while preserving internal natural line breaks for display.
     */
    fun normalizeDisplayedText(rawText: String): String {
        return rawText
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
    }

    /**
     * Checks whether a candidate text edit in the UI should be accepted without silent truncation.
     * Allows editing when the new text is <= 25 words, or when the user is deleting characters
     * from an already-at-limit string.
     */
    fun canAcceptTextEdit(previousText: String, candidateText: String): Boolean {
        if (candidateText.length > MAX_TEXT_CHARS) return false
        val candidateWords = countWords(candidateText)
        if (candidateWords <= MAX_TEXT_WORDS) return true
        // Reject any insertion that pushes word count above 25
        return false
    }

    /**
     * Validates text recall content before database commit.
     */
    fun validateTextRecall(rawText: String): AppResult<String> {
        val normalized = normalizeDisplayedText(rawText)
        if (normalized.isEmpty()) {
            return AppResult.Error(
                FailureReason.EMPTY_TEXT,
                "Text recall cannot be empty."
            )
        }
        val words = countWords(normalized)
        if (words > MAX_TEXT_WORDS) {
            return AppResult.Error(
                FailureReason.WORD_LIMIT_EXCEEDED,
                "Text recall exceeds the 25-word maximum ($words / $MAX_TEXT_WORDS words)."
            )
        }
        if (normalized.length > MAX_TEXT_CHARS) {
            return AppResult.Error(
                FailureReason.WORD_LIMIT_EXCEEDED,
                "Text recall exceeds the maximum character length."
            )
        }
        return AppResult.Success(normalized)
    }

    /**
     * Validates voice recall parameters before database commit.
     */
    fun validateVoiceRecall(
        relativePath: String,
        fileExistsAndNonEmpty: Boolean,
        durationMillis: Long,
        rawTitle: String?
    ): AppResult<ValidatedVoiceInput> {
        if (relativePath.isBlank() || relativePath.startsWith("/") || relativePath.contains("..")) {
            return AppResult.Error(
                FailureReason.VOICE_FILE_MISSING_OR_EMPTY,
                "Voice path must be a valid app-private relative path."
            )
        }
        if (!fileExistsAndNonEmpty) {
            return AppResult.Error(
                FailureReason.VOICE_FILE_MISSING_OR_EMPTY,
                "Recorded audio file is missing or empty."
            )
        }
        if (durationMillis <= 0L || durationMillis > MAX_VOICE_DURATION_MS) {
            return AppResult.Error(
                FailureReason.VOICE_DURATION_INVALID,
                "Voice recall duration must be between 1 ms and 37,000 ms (got $durationMillis ms)."
            )
        }
        val cleanTitle = rawTitle?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanTitle != null && cleanTitle.length > MAX_VOICE_TITLE_CHARS) {
            return AppResult.Error(
                FailureReason.VOICE_TITLE_TOO_LONG,
                "Voice title exceeds $MAX_VOICE_TITLE_CHARS characters."
            )
        }
        return AppResult.Success(
            ValidatedVoiceInput(
                voiceRelativePath = relativePath,
                audioDurationMillis = durationMillis,
                voiceTitle = cleanTitle
            )
        )
    }
}

data class ValidatedVoiceInput(
    val voiceRelativePath: String,
    val audioDurationMillis: Long,
    val voiceTitle: String?
)
