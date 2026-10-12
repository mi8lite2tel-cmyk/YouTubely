// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.model

/**
 * Opaque continuation token for the next page of a list.
 *
 * Only the `StreamProvider` that produced it can interpret it.
 * It is a plain string so it survives process death in saved state.
 */
@JvmInline
value class Page(
    val token: String,
)

/** One page of results plus the token for the next one, or `null` if this is the last page. */
data class Paged<out T>(
    val items: List<T>,
    val nextPage: Page?,
)
