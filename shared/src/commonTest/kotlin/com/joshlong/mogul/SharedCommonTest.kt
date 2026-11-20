package com.joshlong.mogul

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedCommonTest {

    private val mogul = Mogul(7, "Josh")

    @Test
    fun webUrls() {
        assertTrue(isWebUrl("https://spring.io/blog"))
        assertTrue(isWebUrl("HTTP://127.0.0.1:1010/"))
        assertFalse(isWebUrl("chrome://extensions"))
        assertFalse(isWebUrl("https://"))
        assertFalse(isWebUrl("spring.io"))
    }

    @Test
    fun selectionGoesOnTheEndOfTheNote() {
        assertEquals("hi", appendSelection("", "hi"))
        assertEquals("note\n\nhi", appendSelection("note  \n", "hi"))
        assertEquals("note hi", appendSelection("note hi", "hi"))
        assertEquals("note", appendSelection("note", ""))
    }

    @Test
    fun newestFirstComparesMomentsNotText() {
        val sorted = listOf(
            Notable(1, "a", "2026-11-01T10:00:00Z"),
            Notable(2, "b", "2026-11-01T05:00:00-07:00"), // 12:00Z, the newest
            Notable(3, "c", null),
            Notable(4, "d", null),
        ).sortedWith(newestFirst)
        assertEquals(listOf(2, 1, 4, 3), sorted.map { it.id })
    }

    @Test
    fun attachTargets() {
        assertEquals(Target.To("mogul", 7), attachTarget(Attach(), this.mogul))
        val podcasts = Attach(AttachKind.PODCASTS)
        assertTrue(attachTarget(podcasts, this.mogul) is Target.Invalid)
        assertTrue(attachTarget(podcasts.withPick(Pick(1)), this.mogul) is Target.Invalid)
        assertEquals(Target.To("episode", 11), attachTarget(podcasts.withPick(Pick(1, 11)), this.mogul))
        assertEquals(Target.To("podcast", 1), attachTarget(podcasts.withPick(Pick(1, wholeParent = true)), this.mogul))
        // a blog itself can't have notes
        assertTrue(attachTarget(Attach(AttachKind.BLOGS).withPick(Pick(1, wholeParent = true)), this.mogul) is Target.Invalid)
    }

    @Test
    fun picksAreKeptPerKind() {
        val attach = Attach(AttachKind.PODCASTS).withPick(Pick(1, 11)).copy(kind = AttachKind.BLOGS).withPick(Pick(2, 23))
        assertEquals(Pick(1, 11), attach.copy(kind = AttachKind.PODCASTS).pick)
        assertEquals(Pick(2, 23), attach.pick)
    }

    @Test
    fun sharedPages() {
        assertEquals(Page("Spring", "https://spring.io", ""), Page.fromShare("Spring", "https://spring.io"))
        assertEquals(
            Page("", "https://spring.io/blog", "the highlighted bit"),
            Page.fromShare(null, "\"the highlighted bit\"\nhttps://spring.io/blog"),
        )
        assertEquals(Page("", "", "just text"), Page.fromShare(null, "just text"))
    }

    @Test
    fun shortcuts() {
        assertEquals("⌘+Enter", shortcutFor("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)"))
        assertEquals("Ctrl+Enter", shortcutFor("Mozilla/5.0 (X11; Linux x86_64)"))
    }
}
