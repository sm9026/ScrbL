package com.scrbl.app.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Abstraction for capturing the exact commit Instant and ZoneId so tests can deterministically
 * verify calendar day/month/year rollovers and timezone travel invariance.
 */
interface TimeProvider {
    fun nowInstant(): Instant
    fun currentZoneId(): ZoneId
    fun currentLocalDate(): LocalDate = ZonedDateTime.ofInstant(nowInstant(), currentZoneId()).toLocalDate()
    fun elapsedRealtimeMillis(): Long
}

class SystemTimeProvider : TimeProvider {
    override fun nowInstant(): Instant = Instant.now()
    override fun currentZoneId(): ZoneId = ZoneId.systemDefault()
    override fun elapsedRealtimeMillis(): Long = android.os.SystemClock.elapsedRealtime()
}

/**
 * Captures the immutable creation timestamp tuple at successful commit time.
 * Per 02_REQUIREMENTS_AND_RULES.md and 09_DATA_MODEL_AND_STORAGE.md:
 * - Creation timestamp is captured once at successful commit.
 * - capturedLocalDate is derived from the commit Instant + capturedZoneId and stored as ISO-8601 (yyyy-MM-dd)
 *   so later travel or timezone changes never move historical recalls between day stacks.
 */
data class CaptureTimestampSnapshot(
    val createdAtEpochMillis: Long,
    val capturedLocalDate: String,
    val capturedZoneId: String
) {
    companion object {
        fun captureNow(timeProvider: TimeProvider): CaptureTimestampSnapshot {
            val instant = timeProvider.nowInstant()
            val zone = timeProvider.currentZoneId()
            val localDate = ZonedDateTime.ofInstant(instant, zone).toLocalDate()
            return CaptureTimestampSnapshot(
                createdAtEpochMillis = instant.toEpochMilli(),
                capturedLocalDate = localDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                capturedZoneId = zone.id
            )
        }
    }
}

/**
 * Formats stored recall dates and times according to user preferences without mutating stored values.
 */
object RecallDateTimeFormatter {

    fun formatDayStackFace(capturedLocalDateIso: String, patternPref: DateFormatOption): String {
        val date = runCatching { LocalDate.parse(capturedLocalDateIso) }.getOrNull() ?: return capturedLocalDateIso
        val pattern = when (patternPref) {
            DateFormatOption.DD_MM -> "dd/MM"
            DateFormatOption.MM_DD -> "MM/dd"
            DateFormatOption.DD_MMM -> "dd MMM"
        }
        return date.format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
    }

    fun formatExpandedPageDate(capturedLocalDateIso: String, patternPref: DateFormatOption): String {
        val date = runCatching { LocalDate.parse(capturedLocalDateIso) }.getOrNull() ?: return capturedLocalDateIso
        val pattern = when (patternPref) {
            DateFormatOption.DD_MM -> "dd MMM"
            DateFormatOption.MM_DD -> "MMM dd"
            DateFormatOption.DD_MMM -> "dd MMM"
        }
        return date.format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
    }

    fun formatMonthStackFace(monthNumber: Int): String = String.format(Locale.US, "%02d", monthNumber)

    fun formatYearStackFace(year: Int): String = String.format(Locale.US, "'%02d", year % 100)

    fun formatCreationTime(
        createdAtEpochMillis: Long,
        capturedZoneId: String,
        timeFormatOption: TimeFormatOption,
        isSystem24Hour: Boolean = false
    ): String {
        val zone = runCatching { ZoneId.of(capturedZoneId) }.getOrDefault(ZoneId.systemDefault())
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(createdAtEpochMillis), zone)
        val use24h = when (timeFormatOption) {
            TimeFormatOption.HOURS_24 -> true
            TimeFormatOption.HOURS_12 -> false
            TimeFormatOption.SYSTEM -> isSystem24Hour
        }
        val pattern = if (use24h) "HH:mm" else "hh:mm a"
        return zdt.format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
    }

    fun formatPageIndex(oneBasedIndex: Int, totalCount: Int): String {
        val safeIndex = oneBasedIndex.coerceAtLeast(1)
        val safeTotal = totalCount.coerceAtLeast(1)
        return String.format(Locale.US, "%02d / %02d", safeIndex, safeTotal)
    }

    fun formatDurationSeconds(durationMillis: Long): String {
        val totalSeconds = (durationMillis.coerceIn(0L, 37_000L) / 1000L).toInt()
        return String.format(Locale.US, "0:%02d / 0:37", totalSeconds)
    }
}

enum class TimeFormatOption(val storageKey: String, val displayLabel: String) {
    SYSTEM("system_locale", "System default"),
    HOURS_12("12h", "12-hour (03:15 PM)"),
    HOURS_24("24h", "24-hour (15:15)");

    companion object {
        fun fromKey(key: String?): TimeFormatOption =
            entries.firstOrNull { it.storageKey == key } ?: SYSTEM
    }
}

enum class DateFormatOption(val storageKey: String, val displayLabel: String) {
    DD_MM("dd/MM", "DD/MM (08/11)"),
    MM_DD("MM/dd", "MM/DD (11/08)"),
    DD_MMM("dd MMM", "DD MMM (08 Nov)");

    companion object {
        fun fromKey(key: String?): DateFormatOption =
            entries.firstOrNull { it.storageKey == key } ?: DD_MM
    }
}
