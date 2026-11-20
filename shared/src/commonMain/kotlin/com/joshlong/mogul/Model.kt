package com.joshlong.mogul

import kotlin.time.Instant

// the page being clipped: what the browser tab (or, on a phone, the share sheet) hands us
data class Page(val title: String = "", val url: String = "", val selection: String = "") {

    companion object {

        private val URL = Regex("""https?://\S+""")

        // what an app's share sheet sends: the subject is usually the page's title, and the
        // text the url, sometimes with highlighted text quoted in front of it
        fun fromShare(subject: String?, text: String?): Page {
            val body = text.orEmpty().trim()
            val url = URL.find(body)?.value.orEmpty()
            val selection = body.replace(url, "").trim().removeSurrounding("\"").trim()
            return Page(title = subject.orEmpty().trim(), url = url, selection = selection)
        }
    }
}

data class Mogul(val id: Int, val givenName: String?)

// a note, and what it's attached to: type is one of mogul, podcast, episode, or post
data class Note(
    val id: Int,
    val title: String?,
    val note: String?,
    val url: String?,
    val created: String?,
    val type: String,
    val notableId: Int,
)

// a podcast, episode, blog, or post: anything that shows up in an attach-to dropdown
data class Notable(val id: Int, val title: String?, val created: String? = null)

// what the note goes on. GLOBAL is the mogul itself, the default
enum class AttachKind { GLOBAL, PODCASTS, BLOGS }

// the picks for one kind of attachment. wholeParent means the podcast itself, not one of its episodes
data class Pick(val parentId: Int? = null, val childId: Int? = null, val wholeParent: Boolean = false)

// each kind remembers its own picks, so flipping between them loses nothing; only the
// chosen kind's picks count
data class Attach(
    val kind: AttachKind = AttachKind.GLOBAL,
    val picks: Map<AttachKind, Pick> = mapOf(AttachKind.PODCASTS to Pick(), AttachKind.BLOGS to Pick()),
) {
    val pick: Pick get() = this.picks[this.kind] ?: Pick()

    fun withPick(pick: Pick): Attach = copy(picks = this.picks + (this.kind to pick))
}

// the notable to save the note on
sealed interface Target {
    data class To(val type: String, val id: Int) : Target
    data class Invalid(val message: String) : Target
}

// what each kind of attachment lists, and what it attaches the note to
internal enum class AttachSpec(
    val kind: AttachKind,
    val parentLabel: String,
    val childLabel: String,
    val chooseParent: String,
    val chooseChild: String,
    val noParents: String,
    val noChildren: String,
    val parentType: String?, // a blog itself can't have notes, only its posts
    val wholeParent: String?,
    val childType: String,
) {
    PODCASTS(
        AttachKind.PODCASTS, "Podcast", "Episode", "Choose a podcast", "Choose an episode",
        "You have no podcasts yet", "This podcast has no episodes yet", "podcast", "The whole podcast", "episode",
    ),
    BLOGS(
        AttachKind.BLOGS, "Blog", "Post", "Choose a blog", "Choose a post",
        "You have no blogs yet", "This blog has no posts yet", null, null, "post",
    );

    companion object {
        fun of(kind: AttachKind): AttachSpec? = entries.firstOrNull { it.kind == kind }
    }
}

internal fun attachTarget(attach: Attach, mogul: Mogul): Target {
    val spec = AttachSpec.of(attach.kind) ?: return Target.To("mogul", mogul.id)
    val pick = attach.pick
    val parentId = pick.parentId
        ?: return Target.Invalid(spec.chooseParent + ", or attach the note to your account.")
    if (pick.wholeParent && spec.parentType != null)
        return Target.To(spec.parentType, parentId)
    val childId = pick.childId
        ?: return Target.Invalid(spec.chooseChild + ", or attach the note to your account.")
    return Target.To(spec.childType, childId)
}

internal fun isWebUrl(url: String): Boolean =
    Regex("""^https?://[^\s/?#]+(\S*)$""", RegexOption.IGNORE_CASE).matches(url.trim())

// text selected on a page that already has a note goes on the end of it, unless the
// note has it already
internal fun appendSelection(note: String, selection: String): String {
    if (selection.isEmpty() || note.contains(selection))
        return note
    return if (note.isNotBlank()) note.trimEnd() + "\n\n" + selection else selection
}

// newest episode or post first. compares the moments, not the text, so dates written
// with different offsets still sort right; the higher id breaks ties and stands in for a
// missing date
internal val newestFirst: Comparator<Notable> =
    compareByDescending<Notable> { item ->
        item.created?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.DISTANT_PAST
    }.thenByDescending { it.id }
