package com.joshlong.mogul

import androidx.compose.ui.window.ComposeUIViewController
import org.publicvalue.multiplatform.oidc.ExperimentalOpenIdConnect
import org.publicvalue.multiplatform.oidc.appsupport.IosCodeAuthFlowFactory
import org.publicvalue.multiplatform.oidc.tokenstore.SettingsTokenStore
import platform.UIKit.UIViewController

// logs in with ASWebAuthenticationSession, and keeps the tokens in a keychain the app and
// its share extension both read, so one login does for both
@OptIn(ExperimentalOpenIdConnect::class)
private val client: MogulClient by lazy {
    mogulClient(IosCodeAuthFlowFactory(), SettingsTokenStore(SharedKeychain())) }

fun MainViewController() = ComposeUIViewController { App(client = client) }

// the share extension: clips what the share sheet was handed, and [onFinished] closes it
// once it's saved
fun ShareViewController(subject: String?, text: String?, onFinished: () -> Unit): UIViewController =
    ComposeUIViewController { App(page = Page.fromShare(subject, text), client = client, onFinished = onFinished) }
