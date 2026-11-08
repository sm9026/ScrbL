package com.scrbl.app.domain.model

/**
 * Content type of a Recall record.
 */
enum class RecallType {
    TEXT,
    VOICE
}

/**
 * Immutable core Recall domain model with mutable annotation flags (`isAddressed`, `isFavourite`).
 *
 * Binding invariants (02_REQUIREMENTS_AND_RULES.md & 09_DATA_MODEL_AND_STORAGE.md):
 * - `id`, `type`, `textBody`, `voiceRelativePath`, `voiceTitle`, `createdAtEpochMillis`,
 *   `capturedLocalDate`, `capturedZoneId`, and `audioDurationMillis` are strictly immutable after commit.
 * - Only `isAddressed` and `isFavourite` can change after save.
 * - No edit or delete operation exists.
 */
data class Recall(
    val id: String,
    val type: RecallType,
    val textBody: String?,
    val voiceRelativePath: String?,
    val voiceTitle: String?,
    val createdAtEpochMillis: Long,
    val capturedLocalDate: String, // ISO-8601 yyyy-MM-dd in capturedZoneId at commit
    val capturedZoneId: String,
    val audioDurationMillis: Long?,
    val isAddressed: Boolean = false,
    val isFavourite: Boolean = false
) {
    val year: Int
        get() = capturedLocalDate.substringBefore("-").toIntOrNull() ?: 1970

    val month: Int
        get() = capturedLocalDate.split("-").getOrNull(1)?.toIntOrNull() ?: 1

    val dayOfMonth: Int
        get() = capturedLocalDate.split("-").getOrNull(2)?.toIntOrNull() ?: 1

    val yearMonthKey: String
        get() = capturedLocalDate.take(7) // yyyy-MM
}

/**
 * Filter state for day-stack browsing context.
 * Main screen (`mainFilterState`) and Archive (`archiveFilterState`) persist independently.
 * Addressed-only and Unaddressed-only are mutually exclusive (`AddressFilterMode`).
 * Favourite is an independent boolean predicate that can combine with address state and content type.
 */
data class RecallFilterState(
    val favouritesOnly: Boolean = false,
    val addressFilter: AddressFilterMode = AddressFilterMode.ALL,
    val contentTypeFilter: ContentTypeFilterMode = ContentTypeFilterMode.BOTH
) {
    val isActive: Boolean
        get() = favouritesOnly ||
            addressFilter != AddressFilterMode.ALL ||
            contentTypeFilter != ContentTypeFilterMode.BOTH

    fun matches(recall: Recall): Boolean {
        if (favouritesOnly && !recall.isFavourite) return false
        when (addressFilter) {
            AddressFilterMode.ALL -> Unit
            AddressFilterMode.ADDRESSED_ONLY -> if (!recall.isAddressed) return false
            AddressFilterMode.UNADDRESSED_ONLY -> if (recall.isAddressed) return false
        }
        when (contentTypeFilter) {
            ContentTypeFilterMode.BOTH -> Unit
            ContentTypeFilterMode.TEXT_ONLY -> if (recall.type != RecallType.TEXT) return false
            ContentTypeFilterMode.VOICE_ONLY -> if (recall.type != RecallType.VOICE) return false
        }
        return true
    }
}

enum class AddressFilterMode(val storageKey: String, val label: String) {
    ALL("all", "All"),
    ADDRESSED_ONLY("addressed", "Addressed only"),
    UNADDRESSED_ONLY("unaddressed", "Unaddressed only");

    companion object {
        fun fromKey(key: String?): AddressFilterMode =
            entries.firstOrNull { it.storageKey == key } ?: ALL
    }
}

enum class ContentTypeFilterMode(val storageKey: String, val label: String) {
    BOTH("both", "Both (Text & Voice)"),
    TEXT_ONLY("text", "Text only"),
    VOICE_ONLY("voice", "Voice only");

    companion object {
        fun fromKey(key: String?): ContentTypeFilterMode =
            entries.firstOrNull { it.storageKey == key } ?: BOTH
    }
}

/**
 * Read-model projections for the 3-column paper-stack browser.
 * Grouping is always derived from `capturedLocalDate` without duplicating database rows.
 */
data class DayStackGroup(
    val capturedLocalDate: String, // yyyy-MM-dd
    val year: Int,
    val month: Int,
    val dayOfMonth: Int,
    val recallsNewestFirst: List<Recall>
) {
    val newestRecall: Recall?
        get() = recallsNewestFirst.firstOrNull()

    /**
     * Reflects whether all recalls in this day stack have been addressed or whether unaddressed thoughts remain.
     * Used to render the theme-derived shade pair while keeping both types in the same day stack.
     */
    val isAllAddressed: Boolean
        get() = recallsNewestFirst.isNotEmpty() && recallsNewestFirst.all { it.isAddressed }
}

data class MonthStackGroup(
    val year: Int,
    val month: Int, // 1..12
    val isCurrentActiveMonth: Boolean,
    val dayStacksNewestFirst: List<DayStackGroup>
) {
    val yearMonthKey: String
        get() = String.format(java.util.Locale.US, "%04d-%02d", year, month)

    val isAllAddressed: Boolean
        get() = dayStacksNewestFirst.isNotEmpty() && dayStacksNewestFirst.all { it.isAllAddressed }
}

data class YearStackGroup(
    val year: Int,
    val monthStacksNewestFirst: List<MonthStackGroup>
) {
    val isAllAddressed: Boolean
        get() = monthStacksNewestFirst.isNotEmpty() && monthStacksNewestFirst.all { it.isAllAddressed }
}

/**
 * Flattened grid cell items for the 3-column Main / Archive stack grid when completed months
 * are either collapsed (`MonthStackCell`) or expanded/dealt out into day stacks (`FlatMonthCollapseCell` + `DayStackCell`s).
 */
sealed interface StackGridCell {
    val cellKey: String

    data class DayStackCell(
        val dayStack: DayStackGroup,
        val parentExpandedMonth: Int? = null,
        val dealOrderIndex: Int = 0
    ) : StackGridCell {
        override val cellKey: String = "day_${dayStack.capturedLocalDate}"
    }

    data class MonthStackCell(
        val monthStack: MonthStackGroup
    ) : StackGridCell {
        override val cellKey: String = "month_stack_${monthStack.yearMonthKey}"
    }

    data class FlatMonthCollapseCell(
        val monthStack: MonthStackGroup
    ) : StackGridCell {
        override val cellKey: String = "month_flat_${monthStack.yearMonthKey}"
    }

    data class YearStackCell(
        val yearStack: YearStackGroup
    ) : StackGridCell {
        override val cellKey: String = "year_stack_${yearStack.year}"
    }
}
