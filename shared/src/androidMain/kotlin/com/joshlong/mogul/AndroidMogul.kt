package com.joshlong.mogul

import android.content.Context
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
        this.client ?: mogulClient(this.flows, AndroidSettingsTokenStore(context.applicationContext)).also { this.client = it }
}
