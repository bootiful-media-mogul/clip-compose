package com.joshlong.mogul

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

// a blog can have thousands of posts: opening the dropdown, searching it, and picking one
// mustn't bring the app down
@OptIn(ExperimentalTestApi::class)
class SelectTest {

    @Test
    fun aDropdownOfTenThousandPostsOpensSearchesAndPicks() = runComposeUiTest {
        var picked: Int? = null
        val posts = (1..10_000).map { n -> "Post number $n" to { picked = n } }
        setContent {
            MogulTheme {
                Select(selected = null, placeholder = "Choose a post", options = posts)
            }
        }
        onNodeWithText("Choose a post").performClick()
        onNodeWithText("Post number 1").assertIsDisplayed()
        onNodeWithText("Search").performTextInput("9876")
        onNodeWithText("Post number 9876").performClick()
        assertEquals(9876, picked)
    }

    @Test
    fun withASearchTheTypingGoesToMogulAndTheOptionsAreWhatItFound() = runComposeUiTest {
        val searches = mutableListOf<String>()
        var posts by mutableStateOf((2_401..2_500).reversed().map { n -> "Post number $n" to { } })
        setContent {
            MogulTheme {
                Select(
                    selected = null,
                    placeholder = "Choose a post",
                    options = posts,
                    search = { query ->
                        searches += query
                        // what mogul would answer
                        posts = if (query.isBlank()) posts else listOf("Post number 17" to { })
                    },
                    searchPlaceholder = "Search all posts",
                )
            }
        }
        onNodeWithText("Choose a post").performClick()
        onNodeWithText("Search all posts").performTextInput("17")
        assertEquals(listOf("17"), searches)
        onNodeWithText("Post number 17").assertIsDisplayed()
        onNodeWithText("Choose a post").performClick()
        assertEquals(listOf("17", ""), searches, "closing the dropdown clears the search")
    }
}
