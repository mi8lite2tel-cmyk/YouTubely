// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.model

/** Why a new video is not saved automatically. */
enum class SkipReason { Short, Live, TooShort, TooLong, MissingWord, ExcludedWord }

/**
 * What to save from one channel. Every field falls back to the global automatic-download settings
 * when it is `null`, so a channel only states how it differs.
 */
data class AutoDownloadRule(
    /** Highest resolution; `null` = global default. Ignored when [audioOnly]. */
    val maxHeight: Int? = null,
    val audioOnly: Boolean = false,
    /** Newest automatic downloads kept; `null` = global default. */
    val keepLast: Int? = null,
    val skipShorts: Boolean = true,
    val skipLive: Boolean = true,
    /** Shortest and longest video in minutes; 0 = no limit on that side. */
    val minMinutes: Int = 0,
    val maxMinutes: Int = 0,
    /** A title must contain at least one of these (empty = any title). */
    val includeWords: List<String> = emptyList(),
    /** A title containing any of these is skipped. */
    val excludeWords: List<String> = emptyList(),
    /** Remove a download once it was watched to the end. */
    val deleteWatched: Boolean = false,
) {
    /** `null` when [item] should be saved, otherwise the first rule it breaks. */
    fun skipReason(item: StreamItem): SkipReason? =
        typeReason(item.type) ?: lengthReason(item) ?: wordReason(item.title)

    private fun typeReason(type: StreamType): SkipReason? =
        when {
            skipShorts && type == StreamType.Short -> SkipReason.Short
            skipLive && (type == StreamType.Live || type == StreamType.Upcoming) -> SkipReason.Live
            else -> null
        }

    /** A video of unknown length passes; the full info checked before saving knows it. */
    private fun lengthReason(item: StreamItem): SkipReason? {
        val minutes = item.duration?.toMinutes() ?: return null
        return when {
            minMinutes > 0 && minutes < minMinutes -> SkipReason.TooShort
            maxMinutes > 0 && minutes > maxMinutes -> SkipReason.TooLong
            else -> null
        }
    }

    private fun wordReason(title: String): SkipReason? {
        val text = title.lowercase()
        return when {
            includeWords.isNotEmpty() && includeWords.none { it.lowercase() in text } -> SkipReason.MissingWord
            excludeWords.any { it.lowercase() in text } -> SkipReason.ExcludedWord
            else -> null
        }
    }

    companion object {
        val MINUTE_CHOICES = listOf(0, 1, 5, 10, 20, 30, 60, 120)

        /** Words are stored one per line; blank lines and duplicates are dropped. */
        fun parseWords(text: String?): List<String> =
            text
                .orEmpty()
                .split('\n', ',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }

        fun joinWords(words: List<String>): String? = words.joinToString("\n").takeIf { it.isNotEmpty() }
    }
}

/** A daily window in which automatic downloads may run; it wraps past midnight when start > end. */
data class TimeWindow(
    val startHour: Int,
    val endHour: Int,
) {
    init {
        require(startHour in 0..LAST_HOUR && endHour in 0..LAST_HOUR) { "hours must be 0..23" }
    }

    fun contains(hour: Int): Boolean =
        when {
            startHour == endHour -> true
            startHour < endHour -> hour in startHour until endHour
            else -> hour >= startHour || hour < endHour
        }

    /** Minutes from [hour]:[minute] until the window opens; 0 while it is open. */
    fun minutesUntilOpen(
        hour: Int,
        minute: Int,
    ): Int {
        if (contains(hour)) return 0
        val now = hour * MINUTES_PER_HOUR + minute
        val opens = startHour * MINUTES_PER_HOUR
        return (opens - now + MINUTES_PER_DAY) % MINUTES_PER_DAY
    }

    companion object {
        private const val LAST_HOUR = 23
        private const val MINUTES_PER_HOUR = 60
        private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
        val PRESETS = listOf(TimeWindow(0, 6), TimeWindow(1, 7), TimeWindow(22, 7), TimeWindow(9, 18))
    }
}
