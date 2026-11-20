package com.joshlong.mogul

interface Platform {
    val name: String

    // the key chord that saves from the note field, where there's a keyboard to press it on
    val saveShortcut: String? get() = null
}

internal fun shortcutFor(userAgent: String): String =
    if (userAgent.contains("Mac", ignoreCase = true)) "⌘+Enter" else "Ctrl+Enter"

expect fun getPlatform(): Platform
