package com.joshlong.mogul

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.publicvalue.multiplatform.oidc.ExperimentalOpenIdConnect
import org.publicvalue.multiplatform.oidc.OpenIdConnectException
import org.publicvalue.multiplatform.oidc.flows.CodeAuthFlowFactory
import org.publicvalue.multiplatform.oidc.ktor.oidcBearer
import org.publicvalue.multiplatform.oidc.tokenstore.TokenStore

/**
 * Mogul, by way of the gateway's graphql endpoint, with the app's own auth0 token: the
 * same queries and mutations the browser extension sends.
 */
@OptIn(ExperimentalOpenIdConnect::class)
internal class GraphqlMogulClient(
    private val config: MogulConfig,
    private val auth: MogulAuth,
    engine: HttpClientEngine? = null,
) : MogulClient {

    override val studioUrl = this.config.server

    private val http: HttpClient = run {
        val setup: HttpClientConfig<*>.() -> Unit = {
            // a request the gateway won't let through is a redirect to the login page.
            // don't follow it: a redirect here just means "not signed in"
            followRedirects = false
            install(Auth) { oidcBearer(auth.tokens, auth.refreshHandler, auth.oidc) }
        }
        if (engine != null) HttpClient(engine, setup) else HttpClient(setup)
    }

    private var resumed = false

    override suspend fun signIn(): Boolean {
        this.auth.signIn()
        return true
    }

    override suspend fun me(): Mogul {
        if (!this.resumed) {
            this.resumed = true
            this.auth.finishInterruptedSignIn()
        }
        val me = this.graphql("query { me { id givenName } }")["me"].obj()
        return Mogul(me.int("id"), me.string("givenName"))
    }

    override suspend fun notesForUrl(url: String): List<Note> =
        this.graphql(
            "query (\$url: String) { notesForUrl(url: \$url) { id title note url created type notableId } }",
            mapOf("url" to url),
        )["notesForUrl"].list().map {
            Note(it.int("id"), it.string("title"), it.string("note"), it.string("url"), it.string("created"), it.string("type") ?: "mogul", it.int("notableId"))
        }

    override suspend fun podcasts() =
        this.graphql("query { podcasts { id title } }")["podcasts"].notables()

    override suspend fun podcastEpisodes(podcastId: Int) =
        this.graphql(
            "query (\$id: Int) { podcastEpisodesByPodcast(podcastId: \$id) { id title created } }",
            mapOf("id" to podcastId),
        )["podcastEpisodesByPodcast"].notables()

    override suspend fun podcastOfEpisode(episodeId: Int) =
        this.graphql(
            "query (\$id: Int) { podcastEpisodeById(podcastEpisodeId: \$id) { podcastId } }",
            mapOf("id" to episodeId),
        )["podcastEpisodeById"].obj().int("podcastId")

    override suspend fun blogs() =
        this.graphql("query { blogs { id title } }")["blogs"].notables()

    override suspend fun blogPosts(blogId: Int) =
        this.graphql(
            "query (\$id: Int) { blogPostsByBlog(blogId: \$id) { id title created } }",
            mapOf("id" to blogId),
        )["blogPostsByBlog"].notables()

    override suspend fun blogOfPost(postId: Int) =
        this.graphql(
            "query (\$id: Int) { postById(postId: \$id) { blogId } }",
            mapOf("id" to postId),
        )["postById"].obj().int("blogId")

    override suspend fun createNote(type: String, id: Int, title: String?, url: String?, note: String) =
        this.graphql(
            """
            mutation (${'$'}type: String, ${'$'}id: Int, ${'$'}title: String, ${'$'}url: String, ${'$'}note: String) {
                createNote(type: ${'$'}type, id: ${'$'}id, title: ${'$'}title, url: ${'$'}url, note: ${'$'}note)
            }
            """.trimIndent(),
            mapOf("type" to type, "id" to id, "title" to title, "url" to url, "note" to note),
        )["createNote"].isTrue()

    override suspend fun updateNote(id: Int, title: String?, url: String?, note: String, type: String, notableId: Int) =
        this.graphql(
            """
            mutation (${'$'}id: Int, ${'$'}title: String, ${'$'}url: String, ${'$'}note: String, ${'$'}type: String, ${'$'}notableId: Int) {
                updateNote(id: ${'$'}id, title: ${'$'}title, url: ${'$'}url, note: ${'$'}note, notableType: ${'$'}type, notableId: ${'$'}notableId)
            }
            """.trimIndent(),
            mapOf("id" to id, "title" to title, "url" to url, "note" to note, "type" to type, "notableId" to notableId),
        )["updateNote"].isTrue()

    private suspend fun graphql(query: String, variables: Map<String, Any?> = emptyMap()): JsonObject {
        if (this.auth.tokens.getAccessToken() == null)
            throw SignedOutException()
        val body = buildJsonObject {
            put("query", JsonPrimitive(query))
            put("variables", JsonObject(variables.mapValues { (_, value) -> value.toJson() }))
        }
        val response = try {
            this.http.post(this.config.server + "/api/graphql") {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody(body.toString())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: OpenIdConnectException) {
            // the token was turned down and couldn't be refreshed (auth0 issues no refresh
            // token for an api that doesn't allow offline access): time to sign in again
            throw SignedOutException()
        } catch (e: Exception) {
            println("mogul: couldn't reach ${this.config.server}: $e")
            throw IllegalStateException("Couldn't reach ${this.config.server}. Is it running?", e)
        }
        val status = response.status
        if (status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden || status.value in 300..399) {
            val why = response.headers["WWW-Authenticate"] ?: response.headers["Location"] ?: ""
            println("mogul: ${this.config.server} turned the request down: ${status.value} $why")
            println("mogul: the token sent was ${describe(this.auth.tokens.getAccessToken())}")
            throw SignedOutException()
        }
        if (!status.isSuccess())
            throw IllegalStateException("${this.config.server} answered ${status.value}.")
        val answer = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val errors = answer["errors"]?.takeIf { it !is JsonNull }?.jsonArray.orEmpty()
        if (errors.isNotEmpty())
            throw IllegalStateException(errors.joinToString("; ") { it.jsonObject["message"]?.jsonPrimitive?.contentOrNull ?: it.toString() })
        return answer["data"]?.takeIf { it !is JsonNull }?.jsonObject ?: JsonObject(emptyMap())
    }
}

// what a token says about itself (who issued it, for whom, until when), and never the token
@OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
private fun describe(token: String?): String {
    val parts = token?.split(".") ?: return "missing"
    if (parts.size != 3)
        return "not a signed jwt (${parts.size} parts)"
    val claims = runCatching {
        Json.parseToJsonElement(
            kotlin.io.encoding.Base64.UrlSafe.withPadding(kotlin.io.encoding.Base64.PaddingOption.ABSENT_OPTIONAL).decode(parts[1]).decodeToString(),
        ).jsonObject
    }.getOrElse { return "unreadable: $it" }
    return listOf("sub", "iss", "aud", "azp", "scope", "exp").joinToString(", ") { "$it=${claims[it]}" }
}

private fun Any?.toJson(): JsonElement = when (this) {
    null -> JsonNull
    is Number -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    else -> JsonPrimitive(this.toString())
}

private fun JsonElement?.obj(): JsonObject =
    this?.takeIf { it !is JsonNull }?.jsonObject ?: throw IllegalStateException("Mogul didn't find it.")

private fun JsonElement?.list(): List<JsonObject> =
    this?.takeIf { it !is JsonNull }?.jsonArray?.filter { it !is JsonNull }?.map { it.jsonObject }.orEmpty()

private fun JsonElement?.notables(): List<Notable> =
    this.list().map { Notable(it.int("id"), it.string("title"), it.string("created")) }

private fun JsonElement?.isTrue(): Boolean =
    (this as? JsonPrimitive)?.contentOrNull == "true"

private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.int(name: String): Int =
    (this[name] as? JsonPrimitive)?.intOrNull ?: throw IllegalStateException("Mogul sent no $name.")

/**
 * Mogul, signed in to with auth0, or a demo one if this build doesn't say where auth0 is
 * (no mogul.auth0.clientId in local.properties).
 */
@OptIn(ExperimentalOpenIdConnect::class)
fun mogulClient(flows: CodeAuthFlowFactory, tokens: TokenStore, server: String = mogulConfig.server): MogulClient {
    val config = mogulConfig.copy(server = server)
    return if (config.signsIn) GraphqlMogulClient(config, MogulAuth(config, flows, tokens)) else DemoMogulClient()
}
