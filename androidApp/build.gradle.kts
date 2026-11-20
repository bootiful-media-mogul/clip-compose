import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

// auth0 sends the browser back to the app on this scheme: the scheme of mogul.redirectUri,
// which shared/build.gradle.kts reads too
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val redirectUri = providers.gradleProperty("mogul.redirectUri").orNull
    ?: localProperties.getProperty("mogul.redirectUri")
    ?: "com.joshlong.mogul://callback"

android {
    namespace = "com.joshlong.mogul"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.joshlong.mogul"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        manifestPlaceholders["oidcRedirectScheme"] = redirectUri.substringBefore(":")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    // the upload key, for bin/android.sh: from -P or the environment (ORG_GRADLE_PROJECT_...),
    // never checked in. without one, the release build comes out unsigned
    val keystore = providers.gradleProperty("mogul.android.keystore").orNull
    signingConfigs {
        if (keystore != null) create("release") {
            storeFile = file(keystore)
            storePassword = providers.gradleProperty("mogul.android.keystorePassword").get()
            keyAlias = providers.gradleProperty("mogul.android.keyAlias").get()
            keyPassword = providers.gradleProperty("mogul.android.keyPassword").get()
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}