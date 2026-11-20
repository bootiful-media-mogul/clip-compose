# Mogul Clip, multiplatform

The Mogul Clip browser extension's popup (`~/code/mogul/clip`), rebuilt with Compose Multiplatform for Android, iOS, and the web.
It clips a page into Mogul as a note on your account: a title, a URL, the note text, and what to attach it to (your account, a podcast episode, or a blog post).

* The look is the extension's: the studio's day palette, Space Grotesk and IBM Plex (converted to TTF in `shared/src/commonMain/composeResources/font`), and the same three attach-to icons.
* The behavior is the extension's too: one page, one note (a page clipped before reopens its note), highlighted text appended to the note, a lone podcast or blog picked for you, episodes and posts newest first, and Cmd/Ctrl+Enter to save.
* On Android, the app is a share target: share a page from a browser and it fills in the title, URL, and any highlighted text, then closes once it's saved.

## signing in

The apps are oauth clients of their own.
They sign in to auth0 in the system browser (Chrome Custom Tabs on Android, `ASWebAuthenticationSession` on iOS) with PKCE, keep their tokens on the device (an encrypted DataStore on Android, the keychain on iOS), and send the access token to the gateway, which checks it and passes it on to the api.
The gateway's README (`~/code/mogul/gateway/README.md`) describes its side, and the auth0 application the apps need.

Say where Mogul and auth0 are in `local.properties` (not checked in), or with `-P` on the command line:

```properties
mogul.server=http://127.0.0.1:1010
mogul.auth0.domain=https://<tenant>.auth0.com/
mogul.auth0.clientId=<the native auth0 application's client id>
# optional; these are the defaults
mogul.auth0.audience=https://api.mogul.tools
mogul.redirectUri=com.joshlong.mogul://callback
```

`mogul.server` is the gateway, as the phone sees it: from the Android emulator, the machine it runs on is `http://10.0.2.2:1010`.
Debug builds on Android may use plain http to `127.0.0.1`, `10.0.2.2`, and `localhost`; release builds may not.
Without `mogul.auth0.clientId` the app runs against `DemoMogulClient`, an in-memory Mogul, and the web build always does: signing in from a browser page belongs to the studio and its session cookie.

## how it's put together

* `Model.kt` - the page, notes, attach-to choices, and the rules from `popup.js` (validation, sorting, where a note goes).
* `MogulClient.kt` - everything the screen asks of Mogul, and `DemoMogulClient`, an in-memory Mogul.
* `GraphqlMogulClient.kt` - the real one: the extension's graphql queries and mutations, sent to the gateway with the app's token.
* `MogulAuth.kt` - signing in to auth0 (with [kotlin-multiplatform-oidc](https://github.com/kalinjul/kotlin-multiplatform-oidc)), and the settings from `local.properties`, which the `generateMogulConfig` task in `shared/build.gradle.kts` turns into code.
* `ClipViewModel.kt` - the popup's state machine: connecting, signed out, the form, saved.
* `App.kt`, `Theme.kt`, `Glyphs.kt` - the screen.

## to resolve

* **iOS share extension** - the iOS app opens on an empty clip; sharing into it needs a share extension target.
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