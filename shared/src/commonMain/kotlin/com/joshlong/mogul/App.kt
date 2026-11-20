package com.joshlong.mogul

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

private val shape = RoundedCornerShape(Space.radius)

/**
 * The clip screen: saves [page] to Mogul as a note on the signed-in mogul.
 * [onFinished] runs a moment after a save, as the popup closes itself; without one, the
 * screen starts over on the same page.
 */
@Composable
fun App(
    page: Page = Page(),
    client: MogulClient = remember { DemoMogulClient() },
    onFinished: (() -> Unit)? = null,
) {
    MogulTheme {
        val model = viewModel { ClipViewModel(client, page) }
        val state by model.ui.collectAsStateWithLifecycle()
        val uris = LocalUriHandler.current

        // back in the foreground: what was created in the studio meanwhile should be pickable
        LifecycleStartEffect(model) {
            model.refresh()
            onStopOrDispose { }
        }

        LaunchedEffect(state.screen) {
            if (state.screen == Screen.SAVED) {
                delay(1500.milliseconds)
                onFinished?.invoke() ?: model.connect()
            }
        }

        Column(Modifier.fillMaxSize().background(Palette.bg)) {
            TopBar(state.mogul?.givenName.orEmpty())
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Panel(state.panelTitle) {
                    when (state.screen) {
                        Screen.LOADING -> Paragraph("Connecting to Mogul…", LocalType.current.body.copy(color = Palette.muted))
                        Screen.SIGNED_OUT -> SignedOut { model.signIn { uris.openUri(client.studioUrl) } }
                        Screen.FORM -> ClipForm(state, model)
                        Screen.SAVED -> Saved(state.savedMessage) { uris.openUri(client.studioUrl) }
                        Screen.NONE -> Unit
                    }
                    state.error?.let {
                        Text(it, Modifier.padding(top = if (state.screen == Screen.NONE) 0.dp else Space.gutter), style = LocalType.current.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(user: String) {
    val type = LocalType.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.topbar)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = Space.gutter, vertical = Space.gutterHalf),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.alignByBaseline()) {
            Text("mogul", Modifier.alignByBaseline(), style = type.brandName)
            Spacer(Modifier.width(6.dp))
            Text("CLIP", Modifier.alignByBaseline(), style = type.brandProduct)
        }
        Text(user, Modifier.alignByBaseline(), style = type.user)
    }
}

// a studio panel: a navy header bar over a light body, rounded, on the sky blue page
@Composable
private fun Panel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .padding(Space.gutter)
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.panelBg),
    ) {
        Text(
            title,
            Modifier.fillMaxWidth().background(Palette.chrome).padding(horizontal = Space.gutter, vertical = 5.dp),
            style = LocalType.current.panelHeader,
        )
        Column(Modifier.padding(Space.gutter), content = content)
    }
}

@Composable
private fun Paragraph(text: String, style: TextStyle = LocalType.current.body) =
    Text(text, Modifier.padding(bottom = Space.gutterHalf), style = style)

@Composable
private fun SignedOut(signIn: () -> Unit) {
    Paragraph("Sign in to Mogul to clip this page.")
    PrimaryButton("Sign in", onClick = signIn)
    Spacer(Modifier.size(Space.gutterHalf))
    Paragraph("You'll sign in to Mogul in your browser, then come right back here.", LocalType.current.small)
}

@Composable
private fun Saved(message: String, openStudio: () -> Unit) {
    Paragraph(message, LocalType.current.savedTitle)
    Text(
        "Open the studio",
        Modifier.clickable(onClick = openStudio),
        style = LocalType.current.body.copy(color = Palette.accent, textDecoration = TextDecoration.Underline),
    )
}

@Composable
private fun ClipForm(state: ClipState, model: ClipViewModel) {
    val type = LocalType.current
    val noteFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { noteFocus.requestFocus() }

    Label("Title", first = true)
    Field(state.title, model::title)

    Label("URL")
    Field(state.url, model::url, textStyle = type.mono, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))

    Label("Note")
    Field(
        state.note,
        model::note,
        Modifier
            .heightIn(min = 90.dp)
            .focusRequester(noteFocus)
            .onPreviewKeyEvent { e ->
                val submit = e.type == KeyEventType.KeyDown && e.key == Key.Enter && (e.isMetaPressed || e.isCtrlPressed)
                if (submit) model.save()
                submit
            },
        singleLine = false,
        minLines = 6,
        placeholder = "Why does this matter?",
    )

    AttachTo(state, model)

    Row(
        Modifier.fillMaxWidth().padding(top = Space.gutter),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(getPlatform().saveShortcut?.let { "$it to save" }.orEmpty(), style = type.small)
        PrimaryButton(state.saveLabel, enabled = !state.saving, onClick = model::save)
    }
}

// attach to: three icon toggles, then the dropdowns
@Composable
private fun AttachTo(state: ClipState, model: ClipViewModel) {
    val attach = state.attach
    Label("Attach to")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.gutterHalf)) {
        KindToggle(AttachKind.GLOBAL, "Everything: your account", attach.kind, model::attachKind)
        KindToggle(AttachKind.PODCASTS, "A podcast episode", attach.kind, model::attachKind)
        KindToggle(AttachKind.BLOGS, "A blog post", attach.kind, model::attachKind)
        if (attach.kind == AttachKind.GLOBAL)
            Text("Your account", Modifier.padding(start = 4.dp), style = LocalType.current.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val spec = AttachSpec.of(attach.kind) ?: return
    val pick = attach.pick
    val parents = state.choices.parents
    Label(spec.parentLabel, small = true)
    Select(
        selected = parents.firstOrNull { it.id == pick.parentId }?.titleOrUntitled(),
        placeholder = if (parents.isEmpty()) spec.noParents else spec.chooseParent,
        options = parents.map { it.titleOrUntitled() to { model.attachParent(it.id) } },
    )
    val children = state.choices.children ?: return
    // a note already on the whole podcast can stay there
    val whole = spec.wholeParent?.takeIf { spec.parentType != null && pick.wholeParent }
    Label(spec.childLabel, small = true)
    Select(
        selected = whole ?: children.firstOrNull { it.id == pick.childId }?.titleOrUntitled(),
        placeholder = if (children.isEmpty()) spec.noChildren else spec.chooseChild,
        options = listOfNotNull(whole?.let { it to { model.attachChild(null) } }) +
                children.map { it.titleOrUntitled() to { model.attachChild(it.id) } },
    )
}

private fun Notable.titleOrUntitled() = this.title?.takeIf { it.isNotBlank() } ?: "(untitled)"

@Composable
private fun KindToggle(kind: AttachKind, description: String, chosen: AttachKind, choose: (AttachKind) -> Unit) {
    val selected = kind == chosen
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        Modifier
            .size(36.dp)
            .clip(shape)
            .background(if (selected) Palette.chrome else Palette.fieldBg)
            .border(1.dp, if (selected || hovered) Palette.chrome else Palette.fieldBorder, shape)
            .hoverable(interaction)
            .selectable(selected, interaction, null, role = Role.RadioButton) { choose(kind) }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Glyph(kind, if (selected) Palette.chromeInk else Palette.chrome, Modifier.size(21.6.dp))
    }
}

@Composable
private fun Label(text: String, first: Boolean = false, small: Boolean = false) {
    val type = LocalType.current
    Text(
        text,
        Modifier.padding(top = if (first) 0.dp else Space.gutterHalf, bottom = 4.dp),
        style = if (small) type.label.copy(fontWeight = type.body.fontWeight, fontSize = type.small.fontSize) else type.label,
    )
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = LocalType.current.body,
    singleLine: Boolean = true,
    minLines: Int = 1,
    placeholder: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    // text that arrives from outside (the page, a note clipped before) puts the cursor at its end
    var edited by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val current = if (edited.text == value) edited else TextFieldValue(value, TextRange(value.length))
    BasicTextField(
        value = current,
        onValueChange = {
            edited = it
            if (it.text != value) onValueChange(it.text)
        },
        modifier = modifier.fillMaxWidth(),
        textStyle = textStyle,
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = keyboardOptions,
        interactionSource = interaction,
        cursorBrush = SolidColor(Palette.accent),
        decorationBox = { inner ->
            Box(
                Modifier
                    .background(Palette.fieldBg, shape)
                    .border(if (focused) 2.dp else 1.dp, if (focused) Palette.accent else Palette.fieldBorder, shape)
                    .padding(horizontal = 9.dp, vertical = 7.dp),
            ) {
                if (value.isEmpty() && placeholder != null)
                    Text(placeholder, style = textStyle.copy(color = Palette.muted))
                inner()
            }
        },
    )
}

// a dropdown that looks like the popup's <select>. a blog can have thousands of posts, so only
// the rows on screen are drawn, and a long list gets a search field
@Composable
private fun Select(selected: String?, placeholder: String, options: List<Pair<String, () -> Unit>>) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var width by remember { mutableStateOf(0) }
    val type = LocalType.current
    val close = {
        expanded = false
        query = ""
    }
    Box(Modifier.fillMaxWidth().onSizeChanged { width = it.width }) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Palette.fieldBg)
                .border(if (expanded) 2.dp else 1.dp, if (expanded) Palette.accent else Palette.fieldBorder, shape)
                .clickable(enabled = options.isNotEmpty(), role = Role.DropdownList) { expanded = true }
                .padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                selected ?: placeholder,
                Modifier.weight(1f),
                style = if (selected == null) type.body.copy(color = Palette.muted) else type.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Chevron()
        }
        DropdownMenu(expanded, close, Modifier.width(with(LocalDensity.current) { width.toDp() })) {
            if (options.size > SEARCHABLE)
                Field(query, { query = it }, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), placeholder = "Search")
            val shown = remember(options, query) { options.matching(query) }
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(shown) { (label, choose) ->
                    DropdownMenuItem(
                        text = { Text(label, style = type.body, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        onClick = {
                            close()
                            choose()
                        },
                    )
                }
            }
        }
    }
}

// more choices than this, and the dropdown gets a search field
private const val SEARCHABLE = 12

@Composable
private fun Chevron() = Canvas(Modifier.padding(start = 6.dp).size(width = 10.dp, height = 6.dp)) {
    drawPath(
        Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width / 2, size.height)
            close()
        },
        Palette.chrome,
    )
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background = when {
        !enabled -> Palette.disabled
        hovered -> Palette.accentHover
        else -> Palette.accent
    }
    Text(
        text,
        Modifier
            .clip(shape)
            .background(background)
            .hoverable(interaction)
            .clickable(interaction, null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.5.dp, vertical = 7.5.dp),
        style = LocalType.current.button,
    )
}
