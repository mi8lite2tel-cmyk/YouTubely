// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.model

/** 11-character YouTube video id, e.g. `dQw4w9WgXcQ`. */
@JvmInline
value class VideoId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "VideoId must not be blank" }
    }

    override fun toString(): String = value
}

/** Channel id in the `UC…` form. Handles (`@name`) are resolved by the provider. */
@JvmInline
value class ChannelId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "ChannelId must not be blank" }
    }

    override fun toString(): String = value
}

@JvmInline
value class PlaylistId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "PlaylistId must not be blank" }
    }

    override fun toString(): String = value
}

/** Content region as an ISO 3166-1 alpha-2 code, e.g. `DE`. */
@JvmInline
value class Region(
    val code: String,
) {
    init {
        require(code.length == 2 && code.all { it in 'A'..'Z' }) {
            "Region must be an upper-case ISO 3166-1 alpha-2 code, was '$code'"
        }
    }

    override fun toString(): String = code
}
