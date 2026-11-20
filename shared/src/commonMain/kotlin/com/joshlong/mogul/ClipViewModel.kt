package com.joshlong.mogul

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Screen { LOADING, SIGNED_OUT, FORM, SAVED, NONE }

// the attach-to dropdowns, as drawn for the chosen kind
data class AttachChoices(
    val parents: List<Notable> = emptyList(),
    val children: List<Notable>? = null, // null until a parent is picked
)

data class ClipState(
    val screen: Screen = Screen.LOADING,
    val mogul: Mogul? = null,
    val existing: Note? = null, // the note already clipped for this page, if any
    val title: String = "",
    val url: String = "",
    val note: String = "",
    val attach: Attach = Attach(),
    val choices: AttachChoices = AttachChoices(),
    val saving: Boolean = false,
    val savedMessage: String = "",
    val error: String? = null,
) {
    val panelTitle: String get() = if (this.existing != null) "Your note on this page" else "Clip this page"

    val saveLabel: String
        get() = when {
            this.saving -> "Saving…"
            this.existing != null -> "Update note"
            else -> "Save note"
        }
}

// the clip popup: saves a page to Mogul as a note on the signed-in mogul
class ClipViewModel(private val client: MogulClient, private val page: Page) : ViewModel() {

    private val state = MutableStateFlow(ClipState())

    val ui: StateFlow<ClipState> = this.state.asStateFlow()

    private val lists = mutableMapOf<String, List<Notable>>() // the podcasts, episodes, blogs, and posts loaded so far

    private var attaching: Job? = null

    init {
        connect()
    }

    fun connect() {
        this.state.value = ClipState()
        this.viewModelScope.launch {
            guarded {
                val mogul = client.me()
                state.update { it.copy(mogul = mogul) }
                fillForm()
            }
        }
    }

    // signing in opens the system browser on auth0; closing it without signing in leaves
    // the sign-in screen up, and anything else that goes wrong shows as an error
    fun signIn(openStudio: () -> Unit) {
        this.state.update { it.copy(error = null) }
        this.viewModelScope.launch {
            guarded {
                if (client.signIn()) connect() else openStudio()
            }
        }
    }

    fun title(value: String) = this.state.update { it.copy(title = value) }

    fun url(value: String) = this.state.update { it.copy(url = value) }

    fun note(value: String) = this.state.update { it.copy(note = value) }

    // the popup starts afresh every time it opens; the app's screen lives on in the
    // background, so the podcasts, blogs, episodes, and posts it loaded may be out of date
    // by the time it's back. load them again, keeping what's been picked
    fun refresh() {
        this.lists.clear()
        if (this.state.value.screen == Screen.FORM)
            this.showAttachment()
    }

    fun attachKind(kind: AttachKind) {
        this.state.update { it.copy(error = null, attach = it.attach.copy(kind = kind)) }
        this.showAttachment()
    }

    fun attachParent(id: Int?) {
        this.state.update { it.copy(error = null, attach = it.attach.withPick(Pick(parentId = id))) }
        this.showAttachment()
    }

    // null is "the whole podcast"
    fun attachChild(id: Int?) = this.state.update {
        it.copy(error = null, attach = it.attach.withPick(it.attach.pick.copy(childId = id, wholeParent = id == null)))
    }

    // one page, one note: if this page was clipped before, reopen that note rather than
    // starting another. the newest wins if there happen to be several
    private suspend fun fillForm() {
        val existing = if (isWebUrl(this.page.url)) this.client.notesForUrl(this.page.url).firstOrNull() else null
        val attach = if (existing != null) this.attachmentOf(existing) else Attach()
        this.state.update {
            if (existing != null) it.copy(
                existing = existing,
                title = existing.title.orEmpty(),
                url = existing.url.orEmpty(),
                note = appendSelection(existing.note.orEmpty(), this.page.selection),
                attach = attach,
                screen = Screen.FORM,
            ) else it.copy(
                title = this.page.title,
                url = if (isWebUrl(this.page.url)) this.page.url else "",
                note = this.page.selection,
                screen = Screen.FORM,
            )
        }
        this.showAttachment()
    }

    // where an existing note is attached, in the screen's terms
    private suspend fun attachmentOf(note: Note): Attach {
        val id = note.notableId
        return when (note.type) {
            "episode" -> Attach(AttachKind.PODCASTS).withPick(Pick(this.client.podcastOfEpisode(id), id))
            "podcast" -> Attach(AttachKind.PODCASTS).withPick(Pick(id, wholeParent = true))
            "post" -> Attach(AttachKind.BLOGS).withPick(Pick(this.client.blogOfPost(id), id))
            else -> Attach()
        }
    }

    private suspend fun cached(key: String, load: suspend () -> List<Notable>): List<Notable> =
        this.lists[key] ?: load().also { this.lists[key] = it }

    // loads what the attach-to dropdowns list for the chosen kind
    private fun showAttachment() {
        this.attaching?.cancel()
        this.state.update { it.copy(choices = AttachChoices()) }
        val spec = AttachSpec.of(this.state.value.attach.kind) ?: return
        this.attaching = this.viewModelScope.launch {
            guarded {
                val parents = when (spec) {
                    AttachSpec.PODCASTS -> cached("podcasts") { client.podcasts() }
                    AttachSpec.BLOGS -> cached("blogs") { client.blogs() }
                }
                // with only one podcast or blog, there's nothing to choose
                var pick = state.value.attach.pick
                if (pick.parentId == null && parents.size == 1) {
                    pick = pick.copy(parentId = parents.single().id)
                    state.update { it.copy(attach = it.attach.withPick(pick)) }
                }
                state.update { it.copy(choices = AttachChoices(parents)) }
                val parentId = pick.parentId ?: return@guarded
                val children = when (spec) {
                    AttachSpec.PODCASTS -> cached("podcasts:$parentId") { client.podcastEpisodes(parentId) }
                    AttachSpec.BLOGS -> cached("blogs:$parentId") { client.blogPosts(parentId) }
                }.sortedWith(newestFirst)
                state.update { it.copy(choices = AttachChoices(parents, children)) }
            }
        }
    }

    fun save() {
        val s = this.state.value
        val mogul = s.mogul ?: return
        if (s.saving)
            return
        val title = s.title.trim()
        val url = s.url.trim()
        val error = when {
            url.isNotEmpty() && !isWebUrl(url) -> "The URL has to start with http:// or https://."
            title.isEmpty() && url.isEmpty() && s.note.isBlank() -> "There is nothing to save yet."
            else -> null
        }
        val target = attachTarget(s.attach, mogul)
        if (error != null || target is Target.Invalid) {
            this.state.update { it.copy(error = error ?: (target as Target.Invalid).message) }
            return
        }
        target as Target.To
        this.state.update { it.copy(error = null, saving = true) }
        this.viewModelScope.launch {
            guarded {
                val existing = s.existing
                val saved = if (existing != null)
                    client.updateNote(existing.id, title.ifEmpty { null }, url.ifEmpty { null }, s.note, target.type, target.id)
                else
                    client.createNote(target.type, target.id, title.ifEmpty { null }, url.ifEmpty { null }, s.note)
                if (!saved)
                    throw IllegalStateException("Mogul did not save the note.")
                state.update {
                    it.copy(screen = Screen.SAVED, savedMessage = if (existing != null) "Updated your note." else "Saved to your notes.")
                }
            }
            state.update { it.copy(saving = false) }
        }
    }

    // a signed-out answer shows the sign-in screen; anything else shows as an error
    private suspend fun guarded(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: SignedOutException) {
            this.state.update { it.copy(screen = Screen.SIGNED_OUT) }
        } catch (e: Exception) {
            this.state.update {
                it.copy(
                    screen = if (it.screen == Screen.LOADING) Screen.NONE else it.screen,
                    error = e.message ?: e.toString(),
                )
            }
        }
    }
}
