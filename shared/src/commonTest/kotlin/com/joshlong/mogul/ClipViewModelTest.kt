package com.joshlong.mogul

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ClipViewModelTest {

    // a mogul whose blogs and posts can change while the screen is up, as when the studio
    // is used meanwhile
    private class ChangingMogul : MogulClient {
        val blogs = mutableListOf(Notable(1, "joshlong.com"), Notable(2, "spring.io"))
        val posts = mutableMapOf(1 to mutableListOf(Notable(21, "This week in Spring")))
        override val studioUrl = "https://studio.mogul.test"
        override suspend fun me() = Mogul(7, "Josh")
        override suspend fun notesForUrl(url: String) = emptyList<Note>()
        override suspend fun podcasts() = emptyList<Notable>()
        override suspend fun podcastEpisodes(podcastId: Int) = emptyList<Notable>()
        override suspend fun podcastOfEpisode(episodeId: Int) = error("no podcasts")
        override suspend fun blogs() = this.blogs.toList()
        override suspend fun blogPosts(blogId: Int) = this.posts[blogId].orEmpty().toList()
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
}
