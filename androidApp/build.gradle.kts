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
    // the upload key, for bin/android.sh: from -Pmogul.android.keystore=... and so on, or the
    // environment (MOGUL_ANDROID_KEYSTORE and so on), never checked in. without one, the
    // release build comes out unsigned
    fun signing(name: String) = providers.gradleProperty("mogul.android.$name")
        .orElse(providers.environmentVariable("MOGUL_ANDROID_" + name.replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase()))
    val keystore = signing("keystore").orNull
    signingConfigs {
        if (keystore != null) create("release") {
            storeFile = file(keystore)
            storePassword = signing("keystorePassword").get()
            keyAlias = signing("keyAlias").get()
            keyPassword = signing("keyPassword").get()
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