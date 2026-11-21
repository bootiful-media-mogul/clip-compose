package com.joshlong.mogul

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

// the whole clip screen, as on the phone: an old post, older than the newest hundred the
// dropdown shows, is found by searching and then picked
@OptIn(ExperimentalTestApi::class)
class PickAFoundPostTest {

    // a blog of 2,500 posts, post n written on day n
    private class BigBlog : MogulClient {
        private val posts = (1..2_500).map { n ->
            Notable(n, "Post number $n", (kotlin.time.Instant.parse("2020-01-01T00:00:00Z") + kotlin.time.Duration.parse("${n}d")).toString())
        }
        override val studioUrl = "https://studio.mogul.test"
        override suspend fun signIn() = true
        override suspend fun me() = Mogul(7, "Josh")
        override suspend fun notesForUrl(url: String) = emptyList<Note>()
        override suspend fun podcasts() = emptyList<Notable>()
        override suspend fun podcastEpisodes(podcastId: Int, limit: Int?) = emptyList<Notable>()
        override suspend fun searchEpisodes(podcastId: Int, query: String) = emptyList<Notable>()
        override suspend fun podcastEpisode(episodeId: Int) = error("no episodes")
        override suspend fun podcastOfEpisode(episodeId: Int) = error("no episodes")
        override suspend fun blogs() = listOf(Notable(1, "spring.io"), Notable(2, "joshlong.com"))
        override suspend fun blogPosts(blogId: Int, limit: Int?) = this.posts.newest(limit)
        override suspend fun searchPosts(blogId: Int, query: String) = this.posts.found(query)
        override suspend fun blogPost(postId: Int) = this.posts.first { it.id == postId }
        override suspend fun blogOfPost(postId: Int) = 1
        override suspend fun createNote(type: String, id: Int, title: String?, url: String?, note: String) = true
        override suspend fun updateNote(id: Int, title: String?, url: String?, note: String, type: String, notableId: Int) = true
    }

    @Test
    fun anOldPostFoundBySearchingCanBePicked() = runComposeUiTest {
        setContent { App(Page("t", "https://spring.io"), BigBlog()) }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Attach to").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithContentDescription("A blog post").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Choose a blog").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Choose a blog").performClick()
        onNodeWithText("spring.io").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Choose a post").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Choose a post").performClick()
        onNodeWithText("Search all posts").performTextInput("1717")
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Post number 1717").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Post number 1717").performClick()
        waitForIdle()
        onNodeWithText("Post number 1717").assertIsDisplayed()
    }
}
