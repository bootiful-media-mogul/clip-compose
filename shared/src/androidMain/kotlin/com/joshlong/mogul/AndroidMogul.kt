package com.joshlong.mogul

import android.content.Context
import android.os.Build
import androidx.activity.ComponentActivity
import org.publicvalue.multiplatform.oidc.ExperimentalOpenIdConnect
import org.publicvalue.multiplatform.oidc.appsupport.AndroidCodeAuthFlowFactory
import org.publicvalue.multiplatform.oidc.tokenstore.AndroidSettingsTokenStore

// one of each for the whole process: a login in the browser outlives the activity that
// started it (a rotation, or the activity being destroyed while it's in the background)
@OptIn(ExperimentalOpenIdConnect::class)
object AndroidMogul {

    private val flows = AndroidCodeAuthFlowFactory(useWebView = false)

    private var client: MogulClient? = null

    // has to be called in each activity's onCreate, before the login can show the browser
    fun register(activity: ComponentActivity) = this.flows.registerActivity(activity)

    fun client(context: Context): MogulClient =
        this.client ?: mogulClient(this.flows, AndroidSettingsTokenStore(context.applicationContext), reachable(mogulConfig.server))
            .also { this.client = it }

    // the emulator reaches the machine it runs on at 10.0.2.2: there, 127.0.0.1 is the
    // emulator itself
    private fun reachable(server: String): String =
        if (this.isEmulator()) server.replace(Regex("//(127\\.0\\.0\\.1|localhost)(?=[:/]|$)"), "//10.0.2.2") else server

    private fun isEmulator() =
        Build.HARDWARE in setOf("ranchu", "goldfish") || Build.FINGERPRINT.startsWith("generic") || Build.PRODUCT.startsWith("sdk")
}
