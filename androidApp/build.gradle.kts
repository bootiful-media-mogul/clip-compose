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
    ?: "com.joshlong.mogul.clip://callback"

android {
    namespace = "com.joshlong.mogul"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.joshlong.mogul.clip"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // bin/android.sh sets both for a release: play takes each version code only once
        versionCode = providers.gradleProperty("mogul.versionCode").orNull?.toInt() ?: 1
        versionName = providers.gradleProperty("mogul.versionName").orNull ?: "1.0"
        manifestPlaceholders["oidcRedirectScheme"] = redirectUri.substringBefore(":")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    // bin/android.sh signs the release build, through the android gradle plugin's injected
    // signing properties
    buildTypes {
        release {
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