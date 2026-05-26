/**
 * REQUIREMENTS this file demonstrates:
 *   - withTimeoutOrNull: deadline with null fallback (no exception thrown)
 *   - retryWithBackoff: exponential backoff using Flow.retryWhen operator
 *   - supervisorScope: run sibling coroutines independently (failure of one doesn't cancel others)
 *   - measureTimeMillis equivalent with coroutines
 *
 * LESSONS embedded:
 *   - withTimeout throws; withTimeoutOrNull returns null — choose based on whether you want to handle the timeout or propagate it
 *   - retryWhen vs retry: retryWhen gives you access to cause + attempt count for conditional logic
 *   - supervisorScope vs coroutineScope: in supervisorScope, child failure is isolated
 *   - Exponential backoff formula: delay = baseMs * 2^attempt (capped at maxDelayMs)
 *
 * RELATED DRILL TOPICS: coroutines_webflux, resilience_patterns
 */
package com.major.playground.util

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import kotlin.math.min
import kotlin.math.pow
import kotlin.time.Duration

private val log = LoggerFactory.getLogger("com.major.playground.util.Coroutines")

/**
 * Executes [block] with a [timeout]. Returns null on timeout instead of throwing.
 *
 * LESSON: withTimeoutOrNull vs withTimeout:
 *   withTimeout(duration) { ... }        — throws TimeoutCancellationException on timeout
 *   withTimeoutOrNull(duration) { ... }  — returns null on timeout
 *
 * When to use which:
 *   - withTimeout: you WANT the exception to propagate (e.g., abort the whole pipeline)
 *   - withTimeoutOrNull: you want to handle the timeout locally (return a default, log, continue)
 *
 * Example from RecommendationsController:
 *   val features = withTimeoutOrNull(500.milliseconds) { featureClient.getItemFeatures(id) }
 *   // features == null if the feature store didn't respond in 500ms → use cached defaults
 */
suspend fun <T> withDeadlineOrNull(timeout: Duration, block: suspend () -> T): T? =
    withTimeoutOrNull(timeout) { block() }
        .also { result ->
            if (result == null) {
                log.debug("withDeadlineOrNull: timed out after {}", timeout)
            }
        }

/**
 * Retries a [Flow] with exponential backoff when it fails.
 *
 * @param maxAttempts  maximum number of retry attempts (not counting the first try)
 * @param initialDelay base delay for the first retry
 * @param maxDelay     cap on retry delay
 * @param factor       backoff multiplier (default = 2.0 → exponential)
 * @param retryIf      predicate — return true to retry, false to propagate the exception
 *
 * LESSON: `retryWhen { cause, attempt ->` gives you:
 *   - cause: the exception that caused the failure
 *   - attempt: 0-based attempt index (0 = first retry)
 *   You emit `true` to retry, `false` to stop.
 *
 * LESSON: Exponential backoff formula:
 *   delay = min(initialDelay * factor^attempt, maxDelay)
 * With initialDelay=100ms, factor=2.0:
 *   attempt 0 → 100ms, attempt 1 → 200ms, attempt 2 → 400ms, …
 *
 * LESSON: Why cap at maxDelay?
 *   Without a cap, backoff can grow to hours after many retries.
 *   A cap prevents runaway retry storms while still backing off meaningfully.
 */
fun <T> Flow<T>.retryWithBackoff(
    maxAttempts: Int = 3,
    initialDelay: Duration = Duration.parse("PT0.1S"), // 100ms
    maxDelay: Duration = Duration.parse("PT30S"),
    factor: Double = 2.0,
    retryIf: (Throwable) -> Boolean = { true },
): Flow<T> = retryWhen { cause, attempt ->
    if (attempt >= maxAttempts || !retryIf(cause)) {
        log.warn(
            "retryWithBackoff: giving up after {} attempts, last error: {}",
            attempt + 1, cause.message,
        )
        return@retryWhen false // stop retrying — Flow terminates with the exception
    }

    // LESSON: `factor.pow(attempt.toDouble())` — Kotlin stdlib `Double.pow` (Math.pow equivalent)
    val delayMs = min(
        initialDelay.inWholeMilliseconds * factor.pow(attempt.toDouble()).toLong(),
        maxDelay.inWholeMilliseconds,
    )

    log.info(
        "retryWithBackoff: attempt {}/{}, delaying {}ms, cause: {}",
        attempt + 1, maxAttempts, delayMs, cause.message,
    )

    delay(delayMs)
    true // retry
}

/**
 * Runs multiple independent suspend blocks concurrently using supervisorScope.
 * Collects results and errors separately — a failing block does NOT cancel its siblings.
 *
 * Returns a Pair of (successes, errors).
 *
 * LESSON: supervisorScope vs coroutineScope:
 *
 *   coroutineScope { async { A }; async { B } }
 *     If A throws → B is cancelled → scope throws.
 *     Use when: "all or nothing" — any failure aborts the whole group.
 *
 *   supervisorScope { async { A }; async { B } }
 *     If A throws → B continues → scope collects both results.
 *     Use when: "best effort" — partial results are valuable even if some fail.
 *
 * Real example: fetching metadata from multiple feature stores in parallel.
 * If one store is down, we still want results from the others.
 *
 * LESSON: supervisorScope itself does NOT cancel on child failure, but the child's
 * Deferred.await() will rethrow the exception to whoever calls await(). You must
 * wrap each await() in try/catch to collect results gracefully.
 */
suspend fun <T> runAllSettled(
    blocks: List<suspend () -> T>,
): Pair<List<T>, List<Throwable>> = supervisorScope {
    // Launch all blocks as independent children under the supervisor scope.
    val deferreds = blocks.map { block ->
        async { block() }
    }

    val results = mutableListOf<T>()
    val errors = mutableListOf<Throwable>()

    for (deferred in deferreds) {
        try {
            results.add(deferred.await())
        } catch (e: Exception) {
            // LESSON: Catching here is safe — the exception from THIS child is re-thrown
            // by await(). Other children are unaffected (supervisor job).
            errors.add(e)
            log.warn("runAllSettled: one task failed: {}", e.message)
        }
    }

    results to errors
}
