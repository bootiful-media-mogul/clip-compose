package com.joshlong.mogul

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onLast
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals

// searching a podcast's episodes, as on the phone: what mogul finds, best first, from
// production's answer to "cornelia davis"
@OptIn(ExperimentalTestApi::class)
class SearchResultsTest {

    private val found = listOf(
        Notable(427, "Temporal.io's Cornelia Davis", "2026-11-05T21:06:36.659Z"),
        Notable(33, "Pivotal's Senior Director of Technology Cornelia Davis", "2018-10-10T17:12:44.000Z"),
        Notable(289, "Digital Accessibility Architect, Java and Groovy legend, Scott Davis", "2024-07-18T18:15:03.672Z"),
        Notable(54, "The Good Dr. Dave Syer", "2020-01-03T00:21:35.000Z"),
        Notable(315, "PostgreSQL contributor Dave Cramer", "2024-11-29T00:53:21.776Z"),
        Notable(14, "Spring Boot 2.4, and then living legend, CTO, and friend John Davies", "2020-11-12T23:17:13.201Z"),
    )

    private inner class Podcast : MogulClient {
        val searches = mutableListOf<String>()
        private val newest = (1..100).map { n -> Notable(1000 + n, "Episode $n", "2025-01-01T00:00:00Z") }
        override val studioUrl = "https://studio.mogul.test"
        override suspend fun signIn() = true
        override suspend fun me() = Mogul(7, "Josh")
        override suspend fun notesForUrl(url: String) = emptyList<Note>()
        override suspend fun podcasts() = listOf(Notable(1, "A Bootiful Podcast"), Notable(18, "Another"))
        override suspend fun podcastEpisodes(podcastId: Int, limit: Int?) = this.newest
        override suspend fun searchEpisodes(podcastId: Int, query: String): List<Notable> {
            this.searches += query
            delay(200)
            return this@SearchResultsTest.found
        }
        override suspend fun podcastEpisode(episodeId: Int) = error("no")
        override suspend fun podcastOfEpisode(episodeId: Int) = 1
        override suspend fun blogs() = emptyList<Notable>()
        override suspend fun blogPosts(blogId: Int, limit: Int?) = emptyList<Notable>()
        override suspend fun searchPosts(blogId: Int, query: String) = emptyList<Notable>()
        override suspend fun blogPost(postId: Int) = error("no")
        override suspend fun blogOfPost(postId: Int) = 1
        override suspend fun createNote(type: String, id: Int, title: String?, url: String?, note: String) = true
        override suspend fun updateNote(id: Int, title: String?, url: String?, note: String, type: String, notableId: Int) = true
    }

    @Test
    fun whatsFoundIsOnScreenBestFirst() = runComposeUiTest {
        val podcast = Podcast()
        setContent { App(Page("t", "https://spring.io"), podcast) }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Attach to").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithContentDescription("A podcast episode").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Choose a podcast").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Choose a podcast").performClick()
        onNodeWithText("A Bootiful Podcast").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Choose an episode").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Choose an episode").performClick()
        // the phone sends a search per key
        for (c in "cornelia davis")
            onAllNodes(hasSetTextAction()).onLast().performTextInput(c.toString())
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(found.first().title!!).fetchSemanticsNodes().isNotEmpty() }
        waitForIdle()
        assertEquals(listOf("cornelia davis"), podcast.searches, "one search, once typing pauses")
        // the dropdown opens at the bottom of the form, under the keyboard on a phone: what
        // was found has to be scrolled to, or only the first result is seen
        for (n in found)
            onNodeWithText(n.title!!).assertIsDisplayed()
    }
}
