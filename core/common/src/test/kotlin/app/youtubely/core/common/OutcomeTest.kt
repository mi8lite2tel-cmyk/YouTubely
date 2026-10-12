// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OutcomeTest {
    @Test
    fun `success wraps the value`() {
        assertEquals(Outcome.Success(42), outcomeOf { 42 })
    }

    @Test
    fun `exception becomes failure`() {
        val error = IllegalStateException("boom")
        val outcome = outcomeOf { throw error }
        assertEquals(Outcome.Failure(error), outcome)
        assertNull(outcome.getOrNull())
    }

    @Test
    fun `cancellation is rethrown instead of wrapped`() {
        assertFailsWith<CancellationException> {
            outcomeOf { throw CancellationException("cancelled") }
        }
    }

    @Test
    fun `cancelled coroutine stays cancelled`() =
        runTest {
            val job =
                async {
                    outcomeOf {
                        awaitCancellation()
                    }
                }
            yield()
            job.cancel()
            assertTrue(job.isCancelled)
            assertFailsWith<CancellationException> { job.await() }
        }

    @Test
    fun `map transforms success and keeps failure`() {
        assertEquals(Outcome.Success("2"), outcomeOf { 2 }.map(Int::toString))
        val failure: Outcome<Int> = Outcome.Failure(IllegalStateException())
        assertIs<Outcome.Failure>(failure.map(Int::toString))
    }

    @Test
    fun `callbacks run for the matching branch only`() {
        var success: Int? = null
        var failure: Throwable? = null
        outcomeOf { 1 }.onSuccess { success = it }.onFailure { failure = it }
        assertEquals(1, success)
        assertNull(failure)
    }
}
