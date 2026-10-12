// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.common

import kotlin.coroutines.cancellation.CancellationException

/**
 * Result of an operation that can fail with an expected error.
 *
 * Unlike [kotlin.Result], [outcomeOf] never swallows [CancellationException], so it is safe
 * to use inside coroutines without breaking structured concurrency.
 */
sealed interface Outcome<out T> {
    data class Success<out T>(
        val value: T,
    ) : Outcome<T>

    data class Failure(
        val error: Throwable,
    ) : Outcome<Nothing>
}

/** Runs [block] and wraps its result. Cancellation is rethrown, never wrapped. */
inline fun <T> outcomeOf(block: () -> T): Outcome<T> =
    try {
        Outcome.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception,
    ) {
        Outcome.Failure(e)
    }

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> =
    when (this) {
        is Outcome.Success -> Outcome.Success(transform(value))
        is Outcome.Failure -> this
    }

fun <T> Outcome<T>.getOrNull(): T? = (this as? Outcome.Success)?.value

inline fun <T> Outcome<T>.onSuccess(action: (T) -> Unit): Outcome<T> =
    also {
        if (it is Outcome.Success) action(it.value)
    }

inline fun <T> Outcome<T>.onFailure(action: (Throwable) -> Unit): Outcome<T> =
    also {
        if (it is Outcome.Failure) action(it.error)
    }
