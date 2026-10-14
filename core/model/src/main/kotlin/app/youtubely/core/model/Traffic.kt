// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.model

import java.time.LocalDate

/** What the bytes were for. */
enum class TrafficKind {
    /** Streaming video and audio. */
    Video,

    /** Files saved for offline viewing. */
    Download,

    /** Thumbnails and avatars. */
    Images,

    /** Search, channel pages, subscriptions, SponsorBlock and everything else. */
    Other,
}

/** Bytes moved on one day. */
data class DayTraffic(
    val date: LocalDate,
    val mobile: Long,
    val wifi: Long,
) {
    val total: Long get() = mobile + wifi
}

/** What the app has used this month and over the last days; all counted on this device only. */
data class TrafficSummary(
    val monthMobile: Long = 0,
    val monthWifi: Long = 0,
    /** Month total per kind, both networks together. */
    val monthByKind: Map<TrafficKind, Long> = emptyMap(),
    /** The last days, oldest first, including days without traffic. */
    val days: List<DayTraffic> = emptyList(),
) {
    val monthTotal: Long get() = monthMobile + monthWifi
}
