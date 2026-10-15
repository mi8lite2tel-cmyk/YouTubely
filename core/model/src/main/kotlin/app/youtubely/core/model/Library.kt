// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.model

import java.time.Instant

/** A followed channel with the settings that only exist on this device. */
data class Subscription(
    val channel: ChannelRef,
    /** Notify about new videos. */
    val notify: Boolean,
    /** Save new videos for offline viewing. */
    val autoDownload: Boolean,
    /** User-chosen label to filter the feed by. */
    val group: String?,
    val subscribedAt: Instant,
    /** What to save when [autoDownload] is on. */
    val autoRule: AutoDownloadRule = AutoDownloadRule(),
)

/** A video put aside in "Watch later". */
data class SavedVideo(
    val item: StreamItem,
    val addedAt: Instant,
)

/** A playlist kept on this device. */
data class LocalPlaylist(
    val id: Long,
    val name: String,
    val itemCount: Int,
    val thumbnailUrl: String?,
    val updatedAt: Instant,
)

/** A new video of a subscribed channel that background sync has not handled yet. */
data class FreshVideo(
    val item: StreamItem,
    val subscription: Subscription,
)

/** Background refresh of subscriptions, notifications and automatic downloads. */
data class SyncSettings(
    val refreshEnabled: Boolean = true,
    val refreshIntervalHours: Int = DEFAULT_INTERVAL_HOURS,
    val refreshOnWifiOnly: Boolean = false,
    /** New-video notifications are opt-in per channel and need this switch too. */
    val notificationsEnabled: Boolean = true,
    /** Highest resolution of automatic downloads; `null` saves audio only. */
    val autoDownloadMaxHeight: Int? = DEFAULT_AUTO_HEIGHT,
    /** Automatic downloads kept per channel; older ones are removed. */
    val autoDownloadKeepLast: Int = DEFAULT_KEEP_LAST,
    /** Stop saving when downloads take this much space; 0 = no limit. */
    val autoDownloadLimitGb: Int = DEFAULT_LIMIT_GB,
    val autoDownloadOnlyCharging: Boolean = true,
    /** Only start automatic downloads inside this daily window, `null` = any time. */
    val autoDownloadWindow: TimeWindow? = null,
    /** Most videos one run saves, 0 = no limit. Keeps a burst of uploads from filling the phone. */
    val autoDownloadPerRun: Int = DEFAULT_PER_RUN,
    /** Do not save videos that are already in the watch history. */
    val autoDownloadSkipWatched: Boolean = true,
) {
    companion object {
        const val DEFAULT_INTERVAL_HOURS = 6
        const val DEFAULT_AUTO_HEIGHT = 720
        const val DEFAULT_KEEP_LAST = 3
        const val DEFAULT_LIMIT_GB = 5
        const val DEFAULT_PER_RUN = 5
        val PER_RUN_CHOICES = listOf(1, 3, 5, 10, 0)
        val INTERVAL_CHOICES = listOf(1, 3, 6, 12, 24)
        val KEEP_LAST_CHOICES = listOf(1, 3, 5, 10)
        val LIMIT_GB_CHOICES = listOf(0, 2, 5, 10, 20)
        val AUTO_HEIGHT_CHOICES = listOf(1080, 720, 480, 360)
    }
}

/**
 * SponsorBlock: crowd-sourced marks of sponsor reads and other stretches to skip. Off by default.
 * When on, the first 4 characters of a hash of the video id go to [serverUrl] (never the id itself).
 */
data class SponsorBlockSettings(
    val enabled: Boolean = false,
    val categories: Set<String> = DEFAULT_CATEGORIES,
    val serverUrl: String = DEFAULT_SERVER,
) {
    companion object {
        const val DEFAULT_SERVER = "https://sponsor.ajay.app"
        val DEFAULT_CATEGORIES = setOf("sponsor", "selfpromo", "interaction")
        val ALL_CATEGORIES =
            listOf("sponsor", "selfpromo", "interaction", "intro", "outro", "preview", "filler", "music_offtopic")

        /**
         * The server address to save for what the user typed: empty (the default server) for blank
         * input, the trimmed address when it is an https URL, `null` when it is neither. An invalid
         * address must never silently turn into the default server.
         */
        fun normalizeServer(input: String): String? {
            val text = input.trim().trimEnd('/')
            if (text.isEmpty()) return ""
            val host = runCatching { java.net.URI(text) }.getOrNull()
            return text.takeIf { host != null && host.scheme == "https" && !host.host.isNullOrEmpty() }
        }
    }
}

/** A stretch of a video that SponsorBlock suggests to skip. */
data class SponsorSegment(
    val category: String,
    val startMs: Long,
    val endMs: Long,
)
