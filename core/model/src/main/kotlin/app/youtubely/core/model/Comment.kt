// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.model

enum class CommentSort {
    Top,
    Newest,
}

data class Comment(
    val id: String,
    val author: ChannelRef,
    val text: String,
    val publishedAtText: String?,
    val likeCount: Long?,
    val isPinned: Boolean,
    val isHeartedByUploader: Boolean,
    val replyCount: Int,
    /** Token for loading replies, `null` when there are none. */
    val replies: Page?,
)
