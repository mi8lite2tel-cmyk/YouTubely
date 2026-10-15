// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.download

/** The phone has too little room for this download. */
class StorageFullException : Exception(DownloadError.NO_SPACE)

/**
 * What is stored in a failed download's `error`: a short code the screen turns into a message in
 * the user's language, or the raw text of an unexpected error.
 */
object DownloadError {
    const val NO_SPACE = "no_space"
    const val EMPTY_OUTPUT = "empty_output"

    fun of(error: Exception): String = error.message?.takeIf { it.isNotBlank() } ?: error::class.java.simpleName
}
