package com.joshlong.mogul

import androidx.compose.ui.window.ComposeUIViewController
import org.publicvalue.multiplatform.oidc.ExperimentalOpenIdConnect
import org.publicvalue.multiplatform.oidc.appsupport.IosCodeAuthFlowFactory
import org.publicvalue.multiplatform.oidc.tokenstore.IosKeychainTokenStore

// logs in with ASWebAuthenticationSession, and keeps the tokens in the keychain
@OptIn(ExperimentalOpenIdConnect::class)
private val client: MogulClient by lazy { mogulClient(IosCodeAuthFlowFactory(), IosKeychainTokenStore()) }

fun MainViewController() = ComposeUIViewController { App(client = client) }
