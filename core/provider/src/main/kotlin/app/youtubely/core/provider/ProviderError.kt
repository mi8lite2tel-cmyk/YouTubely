// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.provider

/**
 * Expected failures of a [StreamProvider]. The UI maps each case to a localized message
 * and a retry action; [Unknown] is reserved for failures nobody anticipated.
 */
sealed class ProviderError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /** No connection, timeout, TLS failure. Retrying later may help. */
    class Network(
        cause: Throwable,
    ) : ProviderError("Network error", cause)

    /** The content is not available in the user's region. */
    class GeoBlocked(
        cause: Throwable? = null,
    ) : ProviderError("Not available in this region", cause)

    /** Requires sign-in for age verification. The app never bypasses this. */
    class AgeRestricted(
        cause: Throwable? = null,
    ) : ProviderError("Age-restricted content", cause)

    /** Paid, members-only or Premium-only content. The app never bypasses this. */
    class Paid(
        cause: Throwable? = null,
    ) : ProviderError("Paid content", cause)

    /** Deleted, private or terminated. */
    class Removed(
        cause: Throwable? = null,
    ) : ProviderError("Content is no longer available", cause)

    /** The source throttled us (HTTP 429, bot check). */
    class RateLimited(
        cause: Throwable? = null,
    ) : ProviderError("Too many requests", cause)

    /** The source changed and the extractor needs an update. */
    class ExtractorOutdated(
        cause: Throwable? = null,
    ) : ProviderError("Extractor needs an update", cause)

    class Unknown(
        cause: Throwable,
    ) : ProviderError(cause.message ?: "Unknown error", cause)
}
