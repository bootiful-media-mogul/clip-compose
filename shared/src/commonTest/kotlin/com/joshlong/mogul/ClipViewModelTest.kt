package com.joshlong.mogul

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalCoroutinesApi::class)
class ClipViewModelTest {

    // a mogul whose blogs and posts can change while the screen is up, as when the studio
    // is used meanwhile
    private class ChangingMogul : MogulClient {
        val blogs = mutableListOf(Notable(1, "joshlong.com"), Notable(2, "spring.io"))
        val posts: MutableMap<Int, MutableList<Notable>> = mutableMapOf(1 to mutableListOf(Notable(21, "This week in Spring")))
        override val studioUrl = "https://studio.mogul.test"
        override suspend fun me() = Mogul(7, "Josh")
        override suspend fun notesForUrl(url: String) = emptyList<Note>()
        override suspend fun podcasts() = emptyList<Notable>()
        override suspend fun podcastEpisodes(podcastId: Int, limit: Int?) = emptyList<Notable>()
        override suspend fun searchEpisodes(podcastId: Int, query: String) = emptyList<Notable>()
        override suspend fun podcastEpisode(episodeId: Int) = error("no podcasts")
        override suspend fun podcastOfEpisode(episodeId: Int) = error("no podcasts")
        override suspend fun blogs() = this.blogs.toList()
        override suspend fun blogPosts(blogId: Int, limit: Int?) = this.posts[blogId].orEmpty().toList().newest(limit)
        override suspend fun searchPosts(blogId: Int, query: String) = this.posts[blogId].orEmpty().toList().found(query)
        override suspend fun blogPost(postId: Int) = this.posts.values.flatten().first { it.id == postId }
        override suspend fun blogOfPost(postId: Int) = 1
        override suspend fun createNote(type: String, id: Int, title: String?, url: String?, note: String) = true
        override suspend fun updateNote(id: Int, title: String?, url: String?, note: String, type: String, notableId: Int) = true
    }

    @BeforeTest
    fun mainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    @Test
    fun comingBackShowsBlogsAndPostsCreatedMeanwhile() = runTest {
        val mogul = ChangingMogul()
        val model = ClipViewModel(mogul, Page("t", "https://spring.io"))
        model.attachKind(AttachKind.BLOGS)
        model.attachParent(1)
        assertEquals(listOf(1, 2), model.ui.value.choices.parents.map { it.id })
        assertEquals(listOf(21), model.ui.value.choices.children?.map { it.id })

        // meanwhile, in the studio
        mogul.blogs += Notable(3, "the new one")
        mogul.posts.getValue(1) += Notable(22, "a new post", "2099-01-01T00:00:00Z")

        model.refresh()
        assertEquals(listOf(1, 2, 3), model.ui.value.choices.parents.map { it.id })
        assertEquals(listOf(22, 21), model.ui.value.choices.children?.map { it.id })
        assertEquals(Pick(parentId = 1), model.ui.value.attach.pick, "what was picked stays picked")
    }

    // a blog with far more posts than a dropdown should hold: post n was written on day n
    private fun aBigBlog() = ChangingMogul().also { mogul ->
        mogul.posts[1] = (1..2_500).map { n ->
            Notable(n, "Post number $n", "2020-01-01T00:00:00Z".let { kotlin.time.Instant.parse(it) + n.days }.toString())
        }.toMutableList()
    }

    @Test
    fun aBigBlogShowsItsNewestAndSearchesForTheRest() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(this.testScheduler))
        val model = ClipViewModel(this@ClipViewModelTest.aBigBlog(), Page("t", "https://spring.io"))
        model.attachKind(AttachKind.BLOGS)
        model.attachParent(1)
        advanceUntilIdle()
        val choices = model.ui.value.choices
        assertEquals((2_500 downTo 2_401).toList(), choices.children?.map { it.id }, "the newest hundred")
        assertTrue(choices.more, "a full page: there may be more, so the dropdown searches")

        model.searchChildren("1717 NUMBER")
        advanceUntilIdle()
        assertEquals(listOf(1_717), model.ui.value.choices.children?.map { it.id }, "an old post is found by searching")

        model.searchChildren("")
        advanceUntilIdle()
        assertEquals(2_500, model.ui.value.choices.children?.first()?.id, "an empty search goes back to the newest")
    }

    @Test
    fun anOldPickedPostStillShowsThoughItIsNotOnThePage() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(this.testScheduler))
        val mogul = this@ClipViewModelTest.aBigBlog()
        val model = ClipViewModel(mogul, Page("t", "https://spring.io"))
        model.attachKind(AttachKind.BLOGS)
        model.attachParent(1)
        advanceUntilIdle()
        model.searchChildren("number 1717")
        advanceUntilIdle()
        model.attachChild(1_717)
        assertEquals(mogul.blogPost(1_717), model.ui.value.choices.picked)

        // the screen goes away and comes back: the page is the newest again, and post 1717 isn't on it
        model.refresh()
        advanceUntilIdle()
        assertFalse(model.ui.value.choices.children.orEmpty().any { it.id == 1_717 })
        assertEquals(Pick(parentId = 1, childId = 1_717), model.ui.value.attach.pick)
        assertEquals(1_717, model.ui.value.choices.picked?.id, "the picked post is fetched on its own")
    }
}
