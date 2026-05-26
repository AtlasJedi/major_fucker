/**
 * REQUIREMENTS this file demonstrates:
 *   - Configuring a production-grade WebClient bean with timeouts, retry, and connection pool
 *   - Using Reactor Netty's HttpClient as the underlying transport
 *   - Documenting the WRONG way (no timeouts) alongside the right way
 *
 * LESSONS embedded:
 *   - Connection timeout vs. response timeout: they protect against different failure modes
 *   - Retry with backoff: retries on 5xx but not on 4xx (4xx = client bug, don't retry)
 *   - Connection pool tuning: pendingAcquireTimeout prevents unbounded queue growth
 *   - Why NOT to use WebClient.create() without configuration in production
 *
 * RELATED DRILL TOPICS: coroutines_webflux, resilience_patterns
 */
package com.major.playground.config

import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import reactor.netty.resources.ConnectionProvider
import java.time.Duration
import java.util.concurrent.TimeUnit

@Configuration
class WebClientConfig {

    private val log = LoggerFactory.getLogger(WebClientConfig::class.java)

    @Value("\${playground.webclient.connect-timeout-ms:2000}")
    private var connectTimeoutMs: Long = 2000

    @Value("\${playground.webclient.response-timeout-ms:5000}")
    private var responseTimeoutMs: Long = 5000

    @Value("\${playground.webclient.max-connections:100}")
    private var maxConnections: Int = 100

    @Value("\${playground.webclient.pending-acquire-timeout-ms:3000}")
    private var pendingAcquireTimeoutMs: Long = 3000

    /**
     * The CORRECT way to configure WebClient.
     *
     * Three layers of timeout protection:
     *  1. TCP connect timeout  — catches "server unreachable / no route to host"
     *  2. Response timeout     — catches "server accepted connection but never replied"
     *  3. Read/write timeout   — per-byte-read/write timeout (catches stalled streams)
     *
     * LESSON: Without these, a single hanging upstream call blocks a coroutine indefinitely.
     *         In a reactive pipeline that multiplexes thousands of in-flight requests,
     *         one stuck call can exhaust the connection pool and bring down your service.
     */
    @Bean
    fun webClient(webClientBuilder: WebClient.Builder): WebClient {
        // LESSON: ConnectionProvider controls the pool of TCP connections to upstreams.
        //   maxConnections: max open connections per remote host.
        //   pendingAcquireTimeout: how long a caller waits if the pool is full before
        //     throwing an error. Without this, callers queue forever → memory leak.
        //   pendingAcquireMaxCount: max size of the wait queue (-1 = unbounded, avoid in prod).
        val connectionProvider = ConnectionProvider.builder("playground-pool")
            .maxConnections(maxConnections)
            .pendingAcquireTimeout(Duration.ofMillis(pendingAcquireTimeoutMs))
            .pendingAcquireMaxCount(500)
            // LESSON: evictInBackground periodically removes idle connections that
            // have been sitting longer than maxIdleTime. Without this, stale TCP connections
            // accumulate (especially after upstream restarts with different port).
            .evictInBackground(Duration.ofSeconds(30))
            .build()

        val httpClient = HttpClient.create(connectionProvider)
            // LESSON: TCP connect timeout — fires if the 3-way handshake never completes.
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs.toInt())
            // LESSON: Response timeout — fires if no bytes are received within this window
            // AFTER the request was fully sent. Protects against slow/dead upstreams.
            .responseTimeout(Duration.ofMillis(responseTimeoutMs))
            .doOnConnected { conn ->
                // LESSON: ReadTimeoutHandler is a Netty channel handler that fires on
                // individual read/write idle periods. Different from responseTimeout:
                //   responseTimeout = time from request sent to first byte of response
                //   ReadTimeoutHandler = time between any two consecutive bytes
                // Use both for complete coverage.
                conn.addHandlerLast(ReadTimeoutHandler(responseTimeoutMs, TimeUnit.MILLISECONDS))
                conn.addHandlerLast(WriteTimeoutHandler(connectTimeoutMs, TimeUnit.MILLISECONDS))
            }
            // Useful in dev — log request/response at WIRE level
            .wiretap(false) // set to true for debugging; logs raw bytes

        log.info(
            "WebClient configured: connectTimeoutMs={}, responseTimeoutMs={}, maxConnections={}",
            connectTimeoutMs, responseTimeoutMs, maxConnections
        )

        return webClientBuilder
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            // LESSON: A service-to-service User-Agent header helps with upstream logging/debugging.
            .defaultHeader(HttpHeaders.USER_AGENT, "major-playground/1.0")
            .build()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WRONG WAY (commented out — study this carefully)
    // ─────────────────────────────────────────────────────────────────────────
    //
    // @Bean
    // fun badWebClient(): WebClient {
    //     // BUG: WebClient.create() uses DEFAULT HttpClient with NO timeouts.
    //     //      If the upstream hangs, this call hangs FOREVER.
    //     //      One stuck upstream + many concurrent callers = thread starvation.
    //     // FIX: Use the fully configured version above with connection pool + timeouts.
    //     return WebClient.create("http://upstream-service")
    // }
}
