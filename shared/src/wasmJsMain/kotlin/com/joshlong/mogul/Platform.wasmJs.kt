package com.joshlong.mogul

@OptIn(ExperimentalWasmJsInterop::class)
private fun userAgent(): String = js("navigator.userAgent")

class WasmPlatform: Platform {
    override val name: String = "Web with Kotlin/Wasm"

    override val saveShortcut: String = shortcutFor(userAgent())
}

actual fun getPlatform(): Platform = WasmPlatform()
