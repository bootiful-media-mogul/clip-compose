package com.joshlong.mogul

import org.publicvalue.multiplatform.oidc.ExperimentalOpenIdConnect
import org.publicvalue.multiplatform.oidc.OpenIdConnectClient
import org.publicvalue.multiplatform.oidc.flows.CodeAuthFlowFactory
import org.publicvalue.multiplatform.oidc.tokenstore.TokenRefreshHandler
import org.publicvalue.multiplatform.oidc.tokenstore.TokenStore
import org.publicvalue.multiplatform.oidc.types.CodeChallengeMethod

// where the app finds mogul and auth0. the values come from local.properties, by way of
// the generateMogulConfig task in shared/build.gradle.kts
internal data class MogulConfig(
    val server: String, // the gateway
    val auth0Domain: String, // https://<tenant>.auth0.com/
    val auth0ClientId: String, // the auth0 application for the apps: a native one, so no secret
    val auth0Audience: String, // what the api checks tokens for
    val redirectUri: String,
) {
    val signsIn: Boolean get() = this.auth0Domain.isNotEmpty() && this.auth0ClientId.isNotEmpty()
}

/**
 * The app is an oauth client of its own: it logs in to auth0 in the system browser with
 * pkce, keeps its tokens on the device, and sends the access token to the gateway, which
 * checks it and passes it on to the api.
 */
@OptIn(ExperimentalOpenIdConnect::class)
internal class MogulAuth(
    private val config: MogulConfig,
    private val flows: CodeAuthFlowFactory,
    val tokens: TokenStore,
) {

    val oidc: OpenIdConnectClient = OpenIdConnectClient(discoveryUri = config.auth0Domain + ".well-known/openid-configuration") {
        clientId = config.auth0ClientId
        // openid profile email: the api looks the mogul up at auth0's /userinfo with this
        // token the first time it sees them. offline_access: a refresh token, if the api
        // in auth0 allows offline access
        scope = "openid profile email offline_access"
        codeChallengeMethod = CodeChallengeMethod.S256
        redirectUri = config.redirectUri
    }

    val refreshHandler = TokenRefreshHandler(this.tokens)

    suspend fun signIn() {
        if (this.oidc.discoverDocument == null)
            this.oidc.discover()
        // auth0 only issues a token the api can verify when it's asked for the audience
        val response = this.flows.createAuthFlow(this.oidc)
            .getAccessToken(configureAuthUrl = { parameters.append("audience", this@MogulAuth.config.auth0Audience) })
        this.tokens.saveTokens(response)
    }

    // on android the app can be killed while the browser is up. if so, the login carries
    // on when it next starts
    suspend fun finishInterruptedSignIn() {
        val flow = this.flows.createAuthFlow(this.oidc)
        if (flow.canContinueLogin())
            this.tokens.saveTokens(flow.continueLogin())
    }
}
