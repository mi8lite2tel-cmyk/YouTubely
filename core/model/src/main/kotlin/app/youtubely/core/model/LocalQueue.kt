// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.model

/**
 * Ids of play queues that come from this device rather than from YouTube: "Watch later" and the
 * playlists the user made. They travel through the same place as a YouTube playlist id.
 */
object LocalQueue {
    private const val PREFIX = "local:"
    const val WATCH_LATER = "local:later"

    fun playlist(id: Long) = "$PREFIX$id"

    fun isLocal(queueId: String) = queueId.startsWith(PREFIX)

    /** The device playlist a queue id names, or `null` for "Watch later" and anything else. */
    fun playlistIdOf(queueId: String): Long? = queueId.removePrefix(PREFIX).toLongOrNull()
}
