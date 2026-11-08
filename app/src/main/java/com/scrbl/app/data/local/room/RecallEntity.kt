package com.scrbl.app.data.local.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.scrbl.app.domain.model.Recall
import com.scrbl.app.domain.model.RecallType

/**
 * Single Room entity/table (`recalls`) for both TEXT and VOICE thoughts.
 *
 * Indexes match 09_DATA_MODEL_AND_STORAGE.md section 5:
 * - `created_at_epoch_millis` (descending queries for latest 19 widget items and day sequences)
 * - `captured_local_date` (day/month/year stack grouping)
 * - Compound index (`captured_local_date`, `is_addressed`, `is_favourite`, `type`) for filtered day queries
 * - `is_favourite`, `created_at_epoch_millis` for the Favourites view across all years
 */
@Entity(
    tableName = "recalls",
    indices = [
        Index(value = ["created_at_epoch_millis"]),
        Index(value = ["captured_local_date"]),
        Index(
            name = "idx_recalls_day_filters",
            value = ["captured_local_date", "is_addressed", "is_favourite", "type"]
        ),
        Index(
            name = "idx_recalls_favourites",
            value = ["is_favourite", "created_at_epoch_millis"]
        )
    ]
)
data class RecallEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "type")
    val type: String, // "TEXT" or "VOICE"

    @ColumnInfo(name = "text_body")
    val textBody: String?,

    @ColumnInfo(name = "voice_relative_path")
    val voiceRelativePath: String?,

    @ColumnInfo(name = "voice_title")
    val voiceTitle: String?,

    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,

    @ColumnInfo(name = "captured_local_date")
    val capturedLocalDate: String, // ISO-8601 yyyy-MM-dd

    @ColumnInfo(name = "captured_zone_id")
    val capturedZoneId: String,

    @ColumnInfo(name = "audio_duration_millis")
    val audioDurationMillis: Long?,

    @ColumnInfo(name = "is_addressed", defaultValue = "0")
    val isAddressed: Boolean = false,

    @ColumnInfo(name = "is_favourite", defaultValue = "0")
    val isFavourite: Boolean = false
) {
    fun toDomainModel(): Recall {
        return Recall(
            id = id,
            type = if (type == RecallType.VOICE.name) RecallType.VOICE else RecallType.TEXT,
            textBody = textBody,
            voiceRelativePath = voiceRelativePath,
            voiceTitle = voiceTitle,
            createdAtEpochMillis = createdAtEpochMillis,
            capturedLocalDate = capturedLocalDate,
            capturedZoneId = capturedZoneId,
            audioDurationMillis = audioDurationMillis,
            isAddressed = isAddressed,
            isFavourite = isFavourite
        )
    }

    companion object {
        fun fromDomainModel(recall: Recall): RecallEntity {
            return RecallEntity(
                id = recall.id,
                type = recall.type.name,
                textBody = recall.textBody,
                voiceRelativePath = recall.voiceRelativePath,
                voiceTitle = recall.voiceTitle,
                createdAtEpochMillis = recall.createdAtEpochMillis,
                capturedLocalDate = recall.capturedLocalDate,
                capturedZoneId = recall.capturedZoneId,
                audioDurationMillis = recall.audioDurationMillis,
                isAddressed = recall.isAddressed,
                isFavourite = recall.isFavourite
            )
        }
    }
}
