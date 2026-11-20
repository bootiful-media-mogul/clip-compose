package com.joshlong.mogul

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.publicvalue.multiplatform.oidc.ExperimentalOpenIdConnect
import org.publicvalue.multiplatform.oidc.OpenIdConnectClient
import org.publicvalue.multiplatform.oidc.flows.CodeAuthFlow
import org.publicvalue.multiplatform.oidc.flows.CodeAuthFlowFactory
import org.publicvalue.multiplatform.oidc.flows.EndSessionFlow
import org.publicvalue.multiplatform.oidc.tokenstore.SettingsStore
import org.publicvalue.multiplatform.oidc.tokenstore.SettingsTokenStore
import org.publicvalue.multiplatform.oidc.types.AuthCodeRequest
import org.publicvalue.multiplatform.oidc.types.remote.AccessTokenResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalOpenIdConnect::class)
class GraphqlMogulClientTest {

    private val config = MogulConfig(
        server = "https://studio.mogul.test",
        auth0Domain = "https://mogul.auth0.test/",
        auth0ClientId = "clip",
        auth0Audience = "https://api.mogul.tools",
        redirectUri = "com.joshlong.mogul://callback",
    )

    private class MemorySettings : SettingsStore {
        private val values = mutableMapOf<String, String>()
        override suspend fun get(key: String) = this.values[key]
        override suspend fun put(key: String, value: String) { this.values[key] = value }
        override suspend fun remove(key: String) { this.values.remove(key) }
        override suspend fun clear() = this.values.clear()
    }

    // there's no browser in a test: nothing here should try to log in
    private object NoBrowser : CodeAuthFlowFactory {
        override fun createAuthFlow(client: OpenIdConnectClient) = object : CodeAuthFlow {
            override suspend fun startLogin(configureAuthUrl: (URLBuilder.() -> Unit)?): AuthCodeRequest = error("no browser")
            override suspend fun canContinueLogin() = false
            override suspend fun continueLogin(configureTokenExchange: (io.ktor.client.request.HttpRequestBuilder.() -> Unit)?): AccessTokenResponse = error("no browser")
        }

        override fun createEndSessionFlow(client: OpenIdConnectClient): EndSessionFlow = error("no browser")
    }

    private val requests = mutableListOf<HttpRequestData>()

    private suspend fun client(
        signedIn: Boolean = true,
        answer: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): GraphqlMogulClient {
        val tokens = SettingsTokenStore(MemorySettings())
        if (signedIn)
            tokens.saveTokens(accessToken = "access", refreshToken = null, idToken = null)
        val engine = MockEngine { request ->
            this@GraphqlMogulClientTest.requests += request
            answer(request)
        }
        return GraphqlMogulClient(this.config, MogulAuth(this.config, NoBrowser, tokens), engine)
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun HttpRequestData.graphql() = Json.parseToJsonElement((this.body as TextContent).text).jsonObject

    @Test
    fun sendsTheAccessTokenToTheGatewaysGraphqlEndpoint() = runTest {
        val client = client { json("""{"data":{"me":{"id":7,"givenName":"Josh"}}}""") }
        assertEquals(Mogul(7, "Josh"), client.me())
        val request = this@GraphqlMogulClientTest.requests.single()
        assertEquals("https://studio.mogul.test/api/graphql", request.url.toString())
        assertEquals("Bearer access", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun withNoTokenItsSignedOutWithoutAsking() = runTest {
        val client = client(signedIn = false) { json("{}") }
        assertFailsWith<SignedOutException> { client.me() }
        assertTrue(this@GraphqlMogulClientTest.requests.isEmpty())
    }

    @Test
    fun aRedirectToTheLoginPageMeansSignedOut() = runTest {
        val client = client { respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/login")) }
        assertFailsWith<SignedOutException> { client.podcasts() }
    }

    @Test
    fun aRejectedTokenMeansSignedOut() = runTest {
        val client = client { json("", HttpStatusCode.Unauthorized) }
        assertFailsWith<SignedOutException> { client.blogs() }
    }

    @Test
    fun graphqlErrorsShowAsTheirMessages() = runTest {
        val client = client { json("""{"errors":[{"message":"no such podcast"},{"message":"try again"}],"data":null}""") }
        val e = assertFailsWith<IllegalStateException> { client.podcastEpisodes(3) }
        assertEquals("no such podcast; try again", e.message)
    }

    @Test
    fun readsNotesAndListsInTheShapesTheScreenUses() = runTest {
        val client = client { request ->
            val query = request.graphql()["query"]!!.jsonPrimitive.content
            when {
                "notesForUrl" in query -> json("""{"data":{"notesForUrl":[{"id":4,"title":"t","note":"n","url":"https://spring.io","created":"2026-11-01T00:00:00Z","type":"episode","notableId":11}]}}""")
                else -> json("""{"data":{"podcastEpisodesByPodcast":[{"id":11,"title":"one","created":"2026-11-01T00:00:00Z"},null]}}""")
            }
        }
        assertEquals(listOf(Note(4, "t", "n", "https://spring.io", "2026-11-01T00:00:00Z", "episode", 11)), client.notesForUrl("https://spring.io"))
        assertEquals(listOf(Notable(11, "one", "2026-11-01T00:00:00Z")), client.podcastEpisodes(1))
    }

    @Test
    fun savesWithTheVariablesTheApiExpects() = runTest {
        val client = client { json("""{"data":{"createNote":true}}""") }
        assertTrue(client.createNote("episode", 11, null, "https://spring.io", "worth a look"))
        val variables = this@GraphqlMogulClientTest.requests.single().graphql()["variables"]!!.jsonObject
        assertEquals("episode", variables["type"]!!.jsonPrimitive.content)
        assertEquals(11, variables["id"]!!.jsonPrimitive.int)
        assertEquals("null", variables["title"].toString())
        assertEquals("worth a look", variables["note"]!!.jsonPrimitive.content)
    }
}
