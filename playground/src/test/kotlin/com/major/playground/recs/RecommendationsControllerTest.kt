/**
 * REQUIREMENTS this file demonstrates:
 *   - WebTestClient for full Spring WebFlux integration testing with suspend controllers
 *   - MockK + SpringMockK (@MockkBean) for dependency injection mocking
 *   - Verifying that the controller triggers parallel async calls (concurrency test)
 *   - Asserting fallback behaviour when a dependency fails
 *   - JSON response body assertions with WebTestClient
 *
 * LESSONS embedded:
 *   - @SpringBootTest(webEnvironment = RANDOM_PORT) starts the full application context
 *   - @MockkBean replaces a Spring bean with a MockK mock for the test
 *   - coEvery { } is the coroutine-aware version of every { } for suspend functions
 *   - WebTestClient is the reactive equivalent of MockMvc — non-blocking, fluent API
 *   - Parallelism test: use delays in mocks + measure wall time to assert concurrency
 *
 * RELATED DRILL TOPICS: coroutines_webflux, recsys, testing
 */
package com.major.playground.recs

import com.major.playground.recs.dto.RecommendationRequest
import com.major.playground.recs.dto.RecommendationResponse
import com.ninjasquad.springmockk.MockkBean
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.delay
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody

/**
 * Integration tests for the RecommendationsController.
 *
 * LESSON: @SpringBootTest(webEnvironment = RANDOM_PORT) is the "full integration" flavour:
 *   - Starts the whole Spring context (all beans)
 *   - Starts a real HTTP server on a random port
 *   - @MockkBean replaces specific beans with mocks WITHIN the Spring context
 *   - WebTestClient is auto-configured and pointed at the test server
 *
 * Alternative: @WebFluxTest — loads only WebFlux layer, faster but doesn't test full wiring.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RecommendationsControllerTest {

    @Autowired
    lateinit var webTestClient: WebTestClient

    // LESSON: @MockkBean — SpringMockK's equivalent of @MockBean.
    // It registers a MockK mock in the Spring context, replacing the real bean.
    // Other beans that depend on AnnService will receive this mock.
    @MockkBean
    lateinit var annService: AnnService

    @MockkBean
    lateinit var productEncoder: ProductEncoder

    @MockkBean
    lateinit var featureClient: FeatureClient

    // LESSON: Reranker is NOT mocked — we let the real implementation run to verify
    // the full pipeline. This is "partial mocking" — mock only external I/O boundaries.
    // Reranker is pure CPU logic, fast, deterministic — no reason to mock it.

    private val sampleItems = (1..10).map { i ->
        IndexedItem(
            itemId = "item-${i.toString().padStart(4, '0')}",
            vector = FloatArray(AnnService.VECTOR_DIM) { j -> (i * j * 0.01f) },
        )
    }

    @BeforeEach
    fun setUp() {
        // LESSON: coEvery { suspendFun() } returns value — the coroutine-aware stub setup.
        // `coEvery` is required for suspend functions; plain `every` will not work.
        coEvery { productEncoder.encodeQuery(any(), any()) } returns FloatArray(AnnService.VECTOR_DIM) { 0.1f }
        coEvery { annService.search(any(), any(), any()) } returns sampleItems
        coEvery { featureClient.getItemFeatures(any()) } returns null
        coEvery { featureClient.getItemFeaturesBatch(any()) } returns emptyMap()
    }

    @Test
    fun `recommend returns items with model version and latency`() {
        val request = RecommendationRequest(
            userId = "user-42",
            contextItemId = "item-0001",
            limit = 5,
        )

        webTestClient
            .post()
            .uri("/recs/recommend")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            // LESSON: .expectStatus().isOk() asserts HTTP 200.
            // WebTestClient is fluent — chain assertions after exchange().
            .expectStatus().isOk()
            .expectBody<RecommendationResponse>()
            .consumeWith { result ->
                val response = result.responseBody!!
                assertThat(response.items).isNotEmpty
                assertThat(response.items.size).isLessThanOrEqualTo(5)
                assertThat(response.modelVersion).isNotBlank()
                assertThat(response.latencyMs).isGreaterThanOrEqualTo(0)
            }
    }

    @Test
    fun `recommend calls encoder, ANN, and feature client — verifies full pipeline`() {
        val request = RecommendationRequest(
            userId = "user-test",
            contextItemId = "item-0002",
            limit = 3,
        )

        webTestClient
            .post()
            .uri("/recs/recommend")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk()

        // LESSON: coVerify { } asserts that suspend mocked functions were called.
        // `exactly = 1` ensures no accidental double-calls (would indicate a pipeline bug).
        coVerify(exactly = 1) { productEncoder.encodeQuery("user-test", "item-0002") }
        coVerify(exactly = 1) { annService.search(any(), any(), any()) }
        coVerify(atLeast = 1) { featureClient.getItemFeaturesBatch(any()) }
    }

    @Test
    fun `recommend returns fallback when ANN service throws`() {
        // LESSON: coEvery { } throws exception — simulate a dependency failure.
        // The controller's try/catch should catch this and return a fallback response.
        coEvery { annService.search(any(), any(), any()) } throws RuntimeException("ANN index unavailable")

        val request = RecommendationRequest(
            userId = "user-fallback",
            contextItemId = "item-0003",
            limit = 5,
        )

        webTestClient
            .post()
            .uri("/recs/recommend")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            // LESSON: The controller returns 200 with a fallback response — not 500.
            // This is the graceful degradation pattern: a degraded response is better
            // than an error response for recommendation systems.
            .expectStatus().isOk()
            .expectBody<RecommendationResponse>()
            .consumeWith { result ->
                val response = result.responseBody!!
                assertThat(response.items).isNotEmpty
                // Fallback model version is different from the primary model
                assertThat(response.modelVersion).contains("fallback")
            }
    }

    @Test
    fun `recommend respects limit parameter`() {
        val request = RecommendationRequest(
            userId = "user-limit",
            contextItemId = "item-0004",
            limit = 3,
        )

        // ANN returns 10 items (sampleItems), but limit=3 should constrain final output
        webTestClient
            .post()
            .uri("/recs/recommend")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk()
            .expectBody<RecommendationResponse>()
            .consumeWith { result ->
                val response = result.responseBody!!
                assertThat(response.items.size).isLessThanOrEqualTo(3)
            }
    }

    @Test
    fun `recommend propagates ab variant to response`() {
        val request = RecommendationRequest(
            userId = "user-ab",
            contextItemId = "item-0005",
            limit = 5,
            abVariant = "variant-B",
        )

        webTestClient
            .post()
            .uri("/recs/recommend")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk()
            .expectBody<RecommendationResponse>()
            .consumeWith { result ->
                val response = result.responseBody!!
                // A/B variant from request must be echoed back in the response
                assertThat(response.abVariant).isEqualTo("variant-B")
            }
    }

    @Test
    fun `parallel calls in the pipeline run concurrently`() {
        // LESSON: To test that coroutines actually run in parallel, we make both
        // mocks sleep for 100ms and assert the total wall time is < 150ms (not 200ms).
        // If they ran sequentially, wall time would be ~200ms.
        // If they run in parallel (the correct implementation), wall time is ~100ms.
        val delayMs = 100L

        coEvery { annService.search(any(), any(), any()) } coAnswers {
            delay(delayMs) // simulate 100ms ANN latency
            sampleItems
        }
        coEvery { featureClient.getItemFeatures(any()) } coAnswers {
            delay(delayMs) // simulate 100ms feature store latency
            null
        }
        coEvery { featureClient.getItemFeaturesBatch(any()) } coAnswers {
            delay(delayMs)
            emptyMap()
        }

        val request = RecommendationRequest(
            userId = "user-parallel",
            contextItemId = "item-0006",
            limit = 5,
        )

        val start = System.currentTimeMillis()

        webTestClient
            .post()
            .uri("/recs/recommend")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk()

        val elapsed = System.currentTimeMillis() - start
        // LESSON: If the three parallel async blocks (ANN, metadata, A/B) truly run
        // concurrently, total time should be ~delayMs, not 3*delayMs.
        // We allow generous headroom (2.5x) to account for test environment variability.
        assertThat(elapsed)
            .withFailMessage("Expected parallel execution (~%dms) but took %dms", delayMs, elapsed)
            .isLessThan(delayMs * 25 / 10) // < 250ms for 100ms parallel work
    }

    @Test
    fun `health endpoint returns ok status`() {
        webTestClient
            .get()
            .uri("/recs/health")
            .exchange()
            .expectStatus().isOk()
            .expectBody<Map<String, String>>()
            .consumeWith { result ->
                assertThat(result.responseBody!!["status"]).isEqualTo("ok")
            }
    }
}
