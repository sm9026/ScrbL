package com.scrbl.app.core.result

/**
 * Lightweight, explicit domain outcome wrapper so UI and ViewModels handle validation
 * or storage failures cleanly without exceptions leaking or partial state mutations.
 */
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Error(val reason: FailureReason, val message: String) : AppResult<Nothing>
}

enum class FailureReason {
    EMPTY_TEXT,
    WORD_LIMIT_EXCEEDED,
    VOICE_DURATION_INVALID,
    VOICE_FILE_MISSING_OR_EMPTY,
    VOICE_TITLE_TOO_LONG,
    STORAGE_ERROR,
    DATABASE_ERROR,
    RECORD_NOT_FOUND,
    PERMISSION_DENIED,
    INTERRUPTED
}
