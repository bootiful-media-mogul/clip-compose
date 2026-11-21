package com.joshlong.mogul

import kotlinx.coroutines.delay

class SignedOutException : RuntimeException("Signed out of Mogul.")

// everything the clip screen asks of Mogul. these mirror the gateway's graphql queries
// and mutations the browser extension uses
interface MogulClient {

    // where the studio lives, for "open the studio" and "sign in"
    val studioUrl: String

    suspend fun me(): Mogul

    suspend fun notesForUrl(url: String): List<Note>

    suspend fun podcasts(): List<Notable>

    // the episodes whose titles have every word of [query] in them (all of them, for none),
    // newest first, and at most [limit] of them (all of them, for none)
    suspend fun podcastEpisodes(podcastId: Int, query: String? = null, limit: Int? = null): List<Notable>

    suspend fun podcastEpisode(episodeId: Int): Notable

    suspend fun podcastOfEpisode(episodeId: Int): Int

    suspend fun blogs(): List<Notable>

    // the posts whose titles have every word of [query] in them (all of them, for none),
    // newest first, and at most [limit] of them (all of them, for none)
    suspend fun blogPosts(blogId: Int, query: String? = null, limit: Int? = null): List<Notable>

    suspend fun blogPost(postId: Int): Notable

    suspend fun blogOfPost(postId: Int): Int

    suspend fun createNote(type: String, id: Int, title: String?, url: String?, note: String): Boolean

    suspend fun updateNote(id: Int, title: String?, url: String?, note: String, type: String, notableId: Int): Boolean

    // a client that can sign in on its own (rather than by sending the user to the studio) does so here
    suspend fun signIn(): Boolean = false
}

// an in-memory Mogul, so the screen has something to talk to before there's a way to
// sign in from outside a browser (personal access tokens in the gateway)
// what the gateway does with a query and a limit, done in memory
internal fun List<Notable>.page(query: String?, limit: Int?): List<Notable> {
    val matches = this.map { (it.title ?: "") to it }.matching(query.orEmpty()).map { it.second }.newestFirst()
    return if (limit == null) matches else matches.take(limit)
}

class DemoMogulClient(private var signedIn: Boolean = true) : MogulClient {

    override val studioUrl = "https://studio.mogul.tools"

    private val mogul = Mogul(1, "Josh")

    private val podcasts = listOf(Notable(1, "A Bootiful Podcast"))

    private val episodes = mapOf(
        1 to listOf(
            Notable(11, "Spring AI 2.0 with the team", "2026-11-13T09:00:00Z"),
            Notable(12, "Java 27, a whirlwind tour", "2026-11-06T09:00:00Z"),
            Notable(13, "Kotlin Multiplatform and Spring", "2026-10-30T09:00:00-07:00"),
        ),
    )

    private val blogs = listOf(Notable(1, "joshlong.com"), Notable(2, "spring.io"))

    private val posts = mapOf(
        1 to listOf(Notable(21, "This week in Spring", "2026-11-18T08:00:00Z"), Notable(22, "On virtual threads", "2026-10-02T08:00:00Z")),
        2 to listOf(Notable(23, "Spring Boot 4.1 is out", "2026-11-19T08:00:00Z")),
    )

    private val notes = mutableListOf<Note>()

    private suspend fun call() {
        delay(250)
        if (!this.signedIn)
            throw SignedOutException()
    }

    override suspend fun signIn(): Boolean {
        this.signedIn = true
        return true
    }

    override suspend fun me(): Mogul = call().let { this.mogul }

    override suspend fun notesForUrl(url: String) = call().let { this.notes.filter { it.url == url }.sortedByDescending { it.id } }

    override suspend fun podcasts() = call().let { this.podcasts }

    override suspend fun podcastEpisodes(podcastId: Int, query: String?, limit: Int?) =
        call().let { this.episodes[podcastId].orEmpty().page(query, limit) }

    override suspend fun podcastEpisode(episodeId: Int) = call().let { this.episodes.values.flatten().first { it.id == episodeId } }

    override suspend fun podcastOfEpisode(episodeId: Int) =
        call().let { this.episodes.entries.first { e -> e.value.any { it.id == episodeId } }.key }

    override suspend fun blogs() = call().let { this.blogs }

    override suspend fun blogPosts(blogId: Int, query: String?, limit: Int?) =
        call().let { this.posts[blogId].orEmpty().page(query, limit) }

    override suspend fun blogPost(postId: Int) = call().let { this.posts.values.flatten().first { it.id == postId } }

    override suspend fun blogOfPost(postId: Int) =
        call().let { this.posts.entries.first { e -> e.value.any { it.id == postId } }.key }

    override suspend fun createNote(type: String, id: Int, title: String?, url: String?, note: String): Boolean {
        call()
        this.notes += Note((this.notes.maxOfOrNull { it.id } ?: 0) + 1, title, note, url, null, type, id)
        return true
    }

    override suspend fun updateNote(id: Int, title: String?, url: String?, note: String, type: String, notableId: Int): Boolean {
        call()
        val index = this.notes.indexOfFirst { it.id == id }
        if (index < 0)
            return false
        this.notes[index] = this.notes[index].copy(title = title, url = url, note = note, type = type, notableId = notableId)
        return true
    }
}
