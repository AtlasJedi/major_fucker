/**
 * REQUIREMENTS this file demonstrates:
 *   - Testing retryWithBackoff with virtual time (no real delays in tests)
 *   - Testing withDeadlineOrNull: null return on timeout, value return on success
 *   - Testing runAllSettled: partial success, all fail, all succeed
 *   - Verifying retry attempt count and delay sequencing
 *
 * LESSONS embedded:
 *   - runTest + advanceTimeBy: advance virtual clock without waiting real time
 *   - TestCoroutineScheduler controls all delay() calls within the test scope
 *   - `shouldThrow` / `shouldNotThrow` for exception-asserting tests
 *   - currentTime in runTest for asserting elapsed virtual time
 *
 * RELATED DRILL TOPICS: coroutines_webflux, resilience_patterns, testing
 */
package com.major.playground.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class CoroutinesTest {

    // ─────────────────────────────────────────────────────────────────────────
    // withDeadlineOrNull tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `withDeadlineOrNull returns value when block completes within timeout`() = runTest {
        // LESSON: runTest's virtual clock means delay(100) advances the clock by 100ms
        // but takes ~0ms real time.
        val result = withDeadlineOrNull(500.milliseconds) {
            kotlinx.coroutines.delay(100) // fast — within 500ms budget
            "success"
        }
        assertThat(result).isEqualTo("success")
    }

    @Test
    fun `withDeadlineOrNull returns null when block exceeds timeout`() = runTest {
        val result = withDeadlineOrNull(100.milliseconds) {
            kotlinx.coroutines.delay(500) // slow — exceeds 100ms budget
            "too late"
        }
        // LESSON: The block was cancelled after 100ms virtual time.
        // withTimeoutOrNull catches the CancellationException and returns null.
        assertThat(result).isNull()
    }

    @Test
    fun `withDeadlineOrNull handles zero-delay block`() = runTest {
        val result = withDeadlineOrNull(1.seconds) {
            // No delay — completes immediately
            42
        }
        assertThat(result).isEqualTo(42)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // retryWithBackoff tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `retryWithBackoff succeeds on first attempt`() = runTest {
        // LESSON: A Flow that never fails should complete without any retries.
        val results = flow {
            emit("value")
        }
            .retryWithBackoff(maxAttempts = 3)
            .toList()

        assertThat(results).containsExactly("value")
    }

    @Test
    fun `retryWithBackoff retries on failure and succeeds eventually`() = runTest {
        var attempts = 0

        val results = flow {
            attempts++
            if (attempts < 3) throw RuntimeException("Transient error attempt $attempts")
            emit("success after $attempts attempts")
        }
            .retryWithBackoff(
                maxAttempts = 3,
                initialDelay = 10.milliseconds,
                factor = 2.0,
            )
            .toList()

        assertThat(results).containsExactly("success after 3 attempts")
        assertThat(attempts).isEqualTo(3)
    }

    @Test
    fun `retryWithBackoff exhausts retries and re-throws`() = runTest {
        var attempts = 0
        val alwaysFails = flow<String> {
            attempts++
            throw RuntimeException("Always fails (attempt $attempts)")
        }
            .retryWithBackoff(
                maxAttempts = 3,
                initialDelay = 10.milliseconds,
            )

        // LESSON: After maxAttempts retries, retryWhen emits false — the Flow terminates
        // with the original exception. We use a try/catch to assert this.
        var caughtMessage: String? = null
        try {
            alwaysFails.toList()
        } catch (e: RuntimeException) {
            caughtMessage = e.message
        }

        assertThat(caughtMessage).contains("Always fails")
        // LESSON: Original 1 attempt + maxAttempts 3 retries = 4 total executions
        assertThat(attempts).isEqualTo(4)
    }

    @Test
    fun `retryWithBackoff respects retryIf predicate`() = runTest {
        data class PermanentError(val msg: String) : Exception(msg)
        data class TransientError(val msg: String) : Exception(msg)

        var attempts = 0

        val flow = flow<String> {
            attempts++
            when (attempts) {
                1 -> throw TransientError("transient — should retry")
                2 -> throw PermanentError("permanent — should NOT retry")
                else -> emit("should not reach here")
            }
        }
            .retryWithBackoff(
                maxAttempts = 3,
                initialDelay = 10.milliseconds,
                // LESSON: retryIf — only retry TransientError, propagate PermanentError immediately
                retryIf = { cause -> cause is TransientError },
            )

        // LESSON: After attempt 1 (TransientError → retry), attempt 2 (PermanentError → stop).
        // PermanentError propagates to caller.
        var caughtType: String? = null
        try {
            flow.toList()
        } catch (e: Exception) {
            caughtType = e::class.simpleName
        }

        assertThat(caughtType).isEqualTo("PermanentError")
        assertThat(attempts).isEqualTo(2) // 1 transient + 1 permanent = 2
    }

    @Test
    fun `retryWithBackoff applies exponential delays`() = runTest {
        var attempts = 0
        val alwaysFails = flow<String> {
            attempts++
            throw RuntimeException("fail")
        }
            .retryWithBackoff(
                maxAttempts = 3,
                initialDelay = 100.milliseconds,
                factor = 2.0,
                maxDelay = 10.seconds,
            )

        // LESSON: testScheduler.currentTime gives the virtual clock's elapsed milliseconds.
        // advanceTimeBy() / runTest auto-advance: runTest runs the body and advances
        // the virtual clock automatically as coroutines hit delay() calls.
        val startTime = testScheduler.currentTime

        try {
            alwaysFails.toList()
        } catch (_: RuntimeException) {
            // Expected — all retries exhausted
        }

        val totalVirtualTime = testScheduler.currentTime - startTime

        // Expected delays: 100ms (attempt 1) + 200ms (attempt 2) + 400ms (attempt 3) = 700ms
        // LESSON: testScheduler.currentTime is the virtual clock — no real wall-clock wait.
        assertThat(totalVirtualTime)
            .describedAs("Expected ~700ms of virtual delay for 3 exponential backoff retries, got %dms", totalVirtualTime)
            .isGreaterThanOrEqualTo(700L)
            .isLessThan(2000L) // generous upper bound — real factor-of-2 backoff stays well below
    }

    // ─────────────────────────────────────────────────────────────────────────
    // runAllSettled tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `runAllSettled returns all results when all succeed`() = runTest {
        val (results, errors) = runAllSettled(
            listOf(
                { "result-1" },
                { "result-2" },
                { "result-3" },
            ),
        )

        assertThat(results).containsExactlyInAnyOrder("result-1", "result-2", "result-3")
        assertThat(errors).isEmpty()
    }

    @Test
    fun `runAllSettled returns partial results when some fail`() = runTest {
        val (results, errors) = runAllSettled(
            listOf(
                { "success" },
                { throw RuntimeException("task 2 failed") },
                { "also success" },
            ),
        )

        // LESSON: supervisorScope in runAllSettled ensures task 2's failure does not
        // cancel tasks 1 and 3. We get partial results.
        assertThat(results).containsExactlyInAnyOrder("success", "also success")
        assertThat(errors).hasSize(1)
        assertThat(errors[0].message).contains("task 2 failed")
    }

    @Test
    fun `runAllSettled returns all errors when all fail`() = runTest {
        val (results, errors) = runAllSettled(
            listOf(
                { throw RuntimeException("fail 1") },
                { throw RuntimeException("fail 2") },
            ),
        )

        assertThat(results).isEmpty()
        assertThat(errors).hasSize(2)
    }

    @Test
    fun `runAllSettled with empty list returns empty results`() = runTest {
        val (results, errors) = runAllSettled<String>(emptyList())
        assertThat(results).isEmpty()
        assertThat(errors).isEmpty()
    }

    @Test
    fun `runAllSettled sibling is not cancelled when one task fails`() = runTest {
        var sibling2Completed = false

        val (results, errors) = runAllSettled(
            listOf(
                {
                    kotlinx.coroutines.delay(10)
                    throw RuntimeException("task 1 fails after delay")
                },
                {
                    // LESSON: This sibling has a longer delay.
                    // In coroutineScope (non-supervisor), task 1's failure would cancel this.
                    // In supervisorScope, this runs to completion despite task 1 failing.
                    kotlinx.coroutines.delay(50)
                    sibling2Completed = true
                    "sibling completed"
                },
            ),
        )

        // LESSON: Supervisor job — task 2 ran to completion despite task 1's failure
        assertThat(sibling2Completed).isTrue()
        assertThat(results).containsExactly("sibling completed")
        assertThat(errors).hasSize(1)
    }
}
