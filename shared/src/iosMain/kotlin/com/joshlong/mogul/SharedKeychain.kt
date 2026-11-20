package com.joshlong.mogul

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.set
import kotlinx.cinterop.ExperimentalForeignApi
import org.publicvalue.multiplatform.oidc.tokenstore.SettingsStore
import platform.Foundation.CFBridgingRetain
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlock
import platform.Security.kSecAttrService

// the library's own keychain store names its entries after the bundle, which differs between
// the app and its share extension. this one uses a name they share, and saves to the keychain
// access group both list first in their entitlements (Mogul.entitlements)
@OptIn(ExperimentalForeignApi::class, ExperimentalSettingsApi::class, ExperimentalSettingsImplementation::class)
internal class SharedKeychain : SettingsStore {

    private val keychain by lazy {
        KeychainSettings(
            kSecAttrService to CFBridgingRetain("com.joshlong.mogul.clip.auth"),
            kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlock,
        )
    }

    override suspend fun get(key: String): String? = this.keychain.getStringOrNull(key)

    override suspend fun put(key: String, value: String) {
        this.keychain[key] = value
    }

    override suspend fun remove(key: String) = this.keychain.remove(key)

    override suspend fun clear() = this.keychain.clear()
}
