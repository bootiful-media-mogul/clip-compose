# Mogul Clip, multiplatform

The Mogul Clip browser extension's popup (`~/code/mogul/clip`), rebuilt with Compose Multiplatform for Android, iOS, and the web.
It clips a page into Mogul as a note on your account: a title, a URL, the note text, and what to attach it to (your account, a podcast episode, or a blog post).

* The look is the extension's: the studio's day palette, Space Grotesk and IBM Plex (converted to TTF in `shared/src/commonMain/composeResources/font`), and the same three attach-to icons.
* The behavior is the extension's too: one page, one note (a page clipped before reopens its note), highlighted text appended to the note, a lone podcast or blog picked for you, episodes and posts newest first, and Cmd/Ctrl+Enter to save.
* On Android, the app is a share target: share a page from a browser and it fills in the title, URL, and any highlighted text, then closes once it's saved.
* On iOS, the share extension (`iosApp/ShareExtension`) does the same: "Clip to Mogul" in the share sheet shows the clip screen over the app you shared from.
  It shares the app's login through a keychain access group (`iosApp/Configuration/Mogul.entitlements`), so you sign in once, in either.

## signing in

The apps are oauth clients of their own.
They sign in to auth0 in the system browser (Chrome Custom Tabs on Android, `ASWebAuthenticationSession` on iOS) with PKCE, keep their tokens on the device (an encrypted DataStore on Android, the keychain on iOS), and send the access token to the gateway, which checks it and passes it on to the api.
The gateway's README (`~/code/mogul/gateway/README.md`) describes its side, and the auth0 application the apps need.

Where auth0 is, and the auth0 application the apps sign in with, are in `gradle.properties`; neither is a secret, since a native app's client id ships inside it.
Where Mogul is comes from `local.properties` (not checked in), or `-P` on the command line:

```properties
# the gateway, as this machine sees it. the default
mogul.server=http://127.0.0.1:1010
```

The iOS simulator shares this machine's network, so `127.0.0.1` reaches a gateway running here.
The Android emulator reaches this machine at `10.0.2.2` instead, and the app makes that substitution itself when it runs on an emulator.
On a real phone, use this machine's address on the Wi-Fi; for production, `https://studio.mogul.tools`.
Debug builds may use plain http to `127.0.0.1`, `10.0.2.2`, and `localhost`; release builds may not.
On iOS, `NSAllowsLocalNetworking` in `iosApp/iosApp/Info.plist` allows plain http to this machine.
Since Android 17, an app needs the local network permission to reach a private address like `10.0.2.2`; debug builds ask for it when they start.
Without `mogul.auth0.clientId` the app runs against `DemoMogulClient`, an in-memory Mogul, and the web build always does: signing in from a browser page belongs to the studio and its session cookie.

### running on iOS

This needs Xcode (the Command Line Tools alone aren't enough): install it from the App Store, then `sudo xcode-select -s /Applications/Xcode.app`.
Then either pick the `iosApp` run configuration in Android Studio, or open `iosApp/iosApp.xcodeproj` in Xcode and run it on a simulator.
A passkey from another device (the QR code) can't sign in on a simulator or an emulator, which have no Bluetooth; use another way of signing in there.

## building and running from the command line

* `bin/android-dev.sh` builds the app against the gateway on this machine and runs it on the emulator, starting one (the first AVD, or `AVD=<name>`) if none is running.
* `bin/ios-dev.sh` does the same on an iOS simulator (`iPhone 17`, or `SIMULATOR=<name>`), and shows the app's output until you stop it.
* `bin/android.sh` builds the play store bundle and an apk against production (`https://studio.mogul.tools`, or `MOGUL_SERVER=<url>`), in `androidApp/build/outputs`.
  They're signed if the upload key's `ORG_GRADLE_PROJECT_mogul.android.*` settings are set (see the script), and unsigned otherwise.
* `bin/ios.sh` archives the app and its share extension against production and exports an `.ipa` for App Store Connect, in `iosApp/build/release`.
  It signs with the team in the Xcode project, through the Apple account signed in to Xcode, which creates the app ids and provisioning profiles it needs.

## how it's put together

* `Model.kt` - the page, notes, attach-to choices, and the rules from `popup.js` (validation, sorting, where a note goes).
* `MogulClient.kt` - everything the screen asks of Mogul, and `DemoMogulClient`, an in-memory Mogul.
* `GraphqlMogulClient.kt` - the real one: the extension's graphql queries and mutations, sent to the gateway with the app's token.
* `MogulAuth.kt` - signing in to auth0 (with [kotlin-multiplatform-oidc](https://github.com/kalinjul/kotlin-multiplatform-oidc)), and the settings from `local.properties`, which the `generateMogulConfig` task in `shared/build.gradle.kts` turns into code.
* `ClipViewModel.kt` - the popup's state machine: connecting, signed out, the form, saved.
* `App.kt`, `Theme.kt`, `Glyphs.kt` - the screen.

## to resolve

* **signing out** - there's no way to, yet, short of clearing the app's data.

---

This is a Kotlin Multiplatform project targeting Android, iOS, Web.

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
    folder is the appropriate location.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- Web app:
  - Wasm target (faster, modern browsers): `./gradlew :webApp:wasmJsBrowserDevelopmentRun`
  - JS target (slower, supports older browsers): `./gradlew :webApp:jsBrowserDevelopmentRun`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest`
- Web tests:
  - Wasm target: `./gradlew :shared:wasmJsTest`
  - JS target: `./gradlew :shared:jsTest`
- iOS tests: `./gradlew :shared:iosSimulatorArm64Test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/),
[Kotlin/Wasm](https://kotl.in/wasm/)…

We would appreciate your feedback on Compose/Web and Kotlin/Wasm in the public Slack channel [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
If you face any issues, please report them on [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP).