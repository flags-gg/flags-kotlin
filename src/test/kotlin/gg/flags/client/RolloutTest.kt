package gg.flags.client

import gg.flags.model.ApiResponse
import gg.flags.model.Details
import gg.flags.model.FeatureFlag
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class RolloutTest {
    // Shared with the API (orchestrator internal/flags/rollout_test.go) and every SDK; must never drift.
    private val vectors = listOf(
        Triple("new-checkout", "user-1", 63),
        Triple("new-checkout", "user-2", 82),
        Triple("teams", "visitor-abc", 12),
        Triple("", "", 53),
        Triple("émoji ✓", "ключ", 95),
    )

    @Test
    fun `bucket matches the shared vectors`() {
        for ((flag, key, want) in vectors) assertEquals(want, Rollout.bucket(flag, key), "$flag:$key")
    }

    @Test
    fun `applies the percentage and fails closed without a key`() {
        assertFalse(Rollout.inRollout("new-checkout", "user-1", 63)) // bucket 63
        assertTrue(Rollout.inRollout("new-checkout", "user-1", 64))
        assertFalse(Rollout.inRollout("new-checkout", null, 50))
        assertTrue(Rollout.inRollout("new-checkout", null, null))
        assertFalse(Rollout.inRollout("new-checkout", "user-1", 0))
    }

    @Test
    fun `encodes the user for the server's targeting rules`() {
        val encoded = Rollout.encodeContext("u-1", mapOf("email" to "ana@acme.com", "groups" to listOf("beta", "ünï")))
        val decoded = Json.parseToJsonElement(String(java.util.Base64.getDecoder().decode(encoded), Charsets.UTF_8)).toString()
        assertEquals("""{"email":"ana@acme.com","groups":["beta","ünï"],"key":"u-1"}""", decoded)
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `client declares the capability and resolves rollouts per targeting key`() = runTest {
        var capabilities: String? = null
        var sentContext: String? = null
        val engine = MockEngine { req ->
            capabilities = req.headers["X-Flags-Capabilities"]
            sentContext = req.headers["X-Flags-Context"]
            respond(
                json.encodeToString(ApiResponse(60, listOf(FeatureFlag(true, Details("new-checkout", "1"), rollout = 64)))),
                HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = FlagsClient.builder()
            .auth(Auth("p", "a", "e"))
            .httpClient(HttpClient(engine) { install(ContentNegotiation) { json(json) } })
            .withMemoryCache()
            .targetingKey("user-1") // bucket 63 < 64: in
            .build()
        try {
            client.awaitInitialRefresh()
            assertEquals("rollout", capabilities)
            assertTrue(sentContext!!.isNotEmpty())
            assertTrue(client.isEnabled("new-checkout"))

            client.setTargetingKey("user-2") // bucket 82: out
            assertFalse(client.isEnabled("new-checkout"))
        } finally {
            client.close()
        }
    }
}
