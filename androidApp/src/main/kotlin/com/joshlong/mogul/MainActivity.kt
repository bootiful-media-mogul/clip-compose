package com.joshlong.mogul

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

// opened from the launcher, it clips nothing in particular; opened from another app's
// share sheet, it clips what was shared, and closes once it's saved, as the popup does
class MainActivity : ComponentActivity() {

    // debug builds talk to a gateway on this machine, a private address, which since android 17
    // takes this permission (declared in the debug manifest only). ask before connecting
    private val localNetwork = "android.permission.ACCESS_LOCAL_NETWORK"

    private val askForLocalNetwork = registerForActivityResult(ActivityResultContracts.RequestPermission()) { this.show() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AndroidMogul.register(this)
        if (this.needsLocalNetwork()) this.askForLocalNetwork.launch(this.localNetwork) else this.show()
    }

    private fun needsLocalNetwork(): Boolean {
        val requested = this.packageManager.getPackageInfo(this.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        return this.localNetwork in requested && this.checkSelfPermission(this.localNetwork) != PackageManager.PERMISSION_GRANTED
    }

    private fun show() {
        val shared = intent?.takeIf { it.action == Intent.ACTION_SEND }
        val page = shared
            ?.let { Page.fromShare(it.getStringExtra(Intent.EXTRA_SUBJECT), it.getStringExtra(Intent.EXTRA_TEXT)) }
            ?: Page()
        val client = AndroidMogul.client(this)

        setContent {
            App(page = page, client = client, onFinished = shared?.let { { finish() } })
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(Page.fromShare("Spring Boot 4.1 is out", "https://spring.io/blog"))
}
