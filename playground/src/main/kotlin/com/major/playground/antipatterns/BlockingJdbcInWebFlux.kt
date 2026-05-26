/**
 * REQUIREMENTS this file demonstrates:
 *   - The wrong-dispatcher bug: using blocking JDBC inside a WebFlux handler
 *   - Why the correct fix is NOT just "add coroutines" but use the right dispatcher
 *   - The R2DBC alternative (fully non-blocking DB) and when to reach for it
 *   - Simulated JDBC with Dispatchers.IO as the correct shim
 *
 * LESSONS embedded:
 *   - Netty event loop threads must NEVER block — even on DB calls
 *   - withContext(Dispatchers.IO) is the JDBC escape hatch in a WebFlux app
 *   - R2DBC is the non-blocking alternative to JDBC for relational databases
 *   - "Reactive all the way down" vs "pragmatic JDBC on IO dispatcher" trade-offs
 *
 * RELATED DRILL TOPICS: coroutines_webflux
 */
package com.major.playground.antipatterns

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Simulates a "legacy JDBC repository" — represents any blocking data access layer.
 *
 * In real code this would be: JdbcTemplate, HibernateJpaRepository, Spring Data JPA, etc.
 * All of these use blocking JDBC drivers that call Thread.sleep-equivalent operations
 * waiting for the database response.
 */
object LegacyJdbcRepository {
    private val log = LoggerFactory.getLogger(LegacyJdbcRepository::class.java)

    /** Simulates a blocking SELECT query (10ms simulated latency). */
    fun findById(id: String): Map<String, String> {
        log.debug("LegacyJdbc: executing blocking query for id={}", id)
        Thread.sleep(10) // This is what JDBC actually does — blocks the calling thread
        return mapOf("id" to id, "name" to "Product $id", "source" to "legacy_jdbc")
    }
}

/**
 * JDBC in WebFlux — bug and fix demonstration.
 *
 * LESSON: WebFlux is "non-blocking all the way down" by design.
 * When you introduce a blocking call anywhere in the reactive pipeline,
 * you break the non-blocking contract and hurt performance.
 *
 * The WRONG approach:
 *   - Call JDBC directly from a suspend fun in a WebFlux controller
 *   - The suspend function runs on Netty's event loop → JDBC blocks that thread
 *
 * The CORRECT approaches (ranked by preference):
 *   1. Use R2DBC (non-blocking reactive driver for PostgreSQL, MySQL, etc.)
 *      — fully non-blocking, integrates with Spring Data Reactive
 *   2. Use withContext(Dispatchers.IO) to run JDBC on the IO thread pool
 *      — pragmatic choice when migrating existing code; acceptable in production
 *   3. Run JDBC in a separate "blocking" service with its own thread pool
 *      — more isolation, useful if the legacy layer is shared with non-reactive code
 */
@RestController
@RequestMapping("/antipatterns/jdbc")
class BlockingJdbcInWebFlux {

    private val log = LoggerFactory.getLogger(BlockingJdbcInWebFlux::class.java)

    /**
     * BUG: Calling blocking JDBC directly on the event loop thread.
     * curl http://localhost:8080/antipatterns/jdbc/wrong/item-001
     *
     * BUG: LegacyJdbcRepository.findById() calls Thread.sleep() (the JDBC network wait).
     *      This runs on the Netty event loop thread (the coroutine's current dispatcher).
     *      Thread is pinned for 10ms per call.
     *      At 1000 RPS × 10ms = all event loop threads saturated → latency explosion.
     *
     * FIX: See theCorrectWay() below.
     */
    @GetMapping("/wrong/{id}")
    suspend fun theBuggyWay(@PathVariable id: String): Map<String, String> {
        log.warn("BUG: calling blocking JDBC on the event loop thread for id={}", id)

        // BUG: This blocks the Netty event loop thread.
        // Under production load, this collapses throughput to:
        //   throughput = eventLoopThreads / jdbcLatencySeconds
        // e.g., 16 threads / 0.01s = 1600 RPS max. With 100ms JDBC latency: 160 RPS max.
        // A single slow query takes down the entire server.
        val data = LegacyJdbcRepository.findById(id) // BUG: blocking on event loop

        return data + mapOf("warning" to "JDBC called on event loop thread — BUG")
    }

    /**
     * CORRECT: Wrapping blocking JDBC in withContext(Dispatchers.IO).
     * curl http://localhost:8080/antipatterns/jdbc/correct/{id}
     *
     * LESSON: withContext(Dispatchers.IO) switches the coroutine to the IO dispatcher.
     * The IO dispatcher uses a bounded thread pool (default: 64 threads, configurable).
     * JDBC calls block these threads, NOT the event loop threads.
     * After JDBC returns, the coroutine resumes on the original dispatcher.
     *
     * LESSON: IO dispatcher thread pool size can be tuned via:
     *   kotlinx.coroutines.io.parallelism system property
     * Set it to match your DB connection pool size — no point having 64 IO threads
     * if your HikariCP pool only has 10 connections.
     */
    @GetMapping("/correct/{id}")
    suspend fun theCorrectWay(@PathVariable id: String): Map<String, String> {
        log.info("CORRECT: switching to Dispatchers.IO for JDBC call, id={}", id)

        // LESSON: withContext(Dispatchers.IO) — the escape hatch for blocking calls.
        //   - Coroutine suspends on the event loop
        //   - Resumes on an IO pool thread (which CAN block)
        //   - JDBC call executes on IO thread
        //   - After return, coroutine resumes on the original dispatcher
        val data = withContext(Dispatchers.IO) {
            LegacyJdbcRepository.findById(id)
        }

        return data + mapOf("note" to "JDBC safely called on Dispatchers.IO")
    }

    /**
     * Demonstrates what R2DBC would look like — non-blocking alternative.
     * (Stub — R2DBC dependency not included to keep the project minimal.)
     *
     * curl http://localhost:8080/antipatterns/jdbc/r2dbc/{id}
     *
     * LESSON: R2DBC (Reactive Relational Database Connectivity) provides a fully
     * non-blocking driver. The database call SUSPENDS the coroutine instead of
     * blocking a thread. You don't need withContext(Dispatchers.IO) at all.
     *
     * Trade-offs of R2DBC vs JDBC+IO:
     *   R2DBC pros: true non-blocking, better throughput at high concurrency
     *   R2DBC cons: less mature ecosystem, no JPA support, SQL only (no ORM magic)
     *   JDBC+IO pros: works with all existing ORM tools, simpler migration path
     *   JDBC+IO cons: still uses threads (just the IO pool), higher memory at scale
     */
    @GetMapping("/r2dbc/{id}")
    suspend fun r2dbcStub(@PathVariable id: String): Map<String, String> {
        // LESSON: With R2DBC + Spring Data Reactive, a repository call looks like:
        //   val product = productRepository.findById(id) // this is a suspend fun
        // No withContext needed — the R2DBC driver suspends the coroutine at the network wait.
        delay(10) // Simulates the non-blocking network wait (what R2DBC would actually do)

        return mapOf(
            "id" to id,
            "name" to "Product $id (R2DBC stub)",
            "source" to "r2dbc_stub",
            "note" to "With real R2DBC: no withContext needed, fully non-blocking",
        )
    }
}
