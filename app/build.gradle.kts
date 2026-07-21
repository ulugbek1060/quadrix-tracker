import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.appdistribution)
}

// Firebase is only used for App Distribution updates, so a missing google-services.json must
// not stop the app from building — otherwise nothing is testable until the Firebase project
// exists. UpdateManager degrades gracefully when Firebase is absent at runtime.
val hasFirebaseConfig = project.file("google-services.json").exists()
if (hasFirebaseConfig) {
    apply(plugin = "com.google.gms.google-services")
} else {
    logger.lifecycle(
        "Tracker: app/google-services.json not found — building without Firebase. " +
            "In-app updates will be unavailable in this build."
    )
}

// Optional release keystore. Create `keystore.properties` in the project root with
// storeFile / storePassword / keyAlias / keyPassword to sign the builds you upload to
// Firebase App Distribution. Without it, release builds fall back to the debug key.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseKeystore = keystoreProperties.containsKey("storeFile")

android {
    namespace = "com.tracker.quadrix"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.tracker.quadrix"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // >>> PLACEHOLDER <<< Replace with the real backend once the API is available.
        // Must end with a trailing slash. Endpoint paths live in data/api/ApiConfig.kt.
        buildConfigField("String", "API_BASE_URL", "\"https://api.example.com/\"")
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// `./gradlew assembleRelease appDistributionUploadRelease` builds and ships a new version.
// Testers are notified in the App Distribution app; the in-app SDK check lives in UpdateManager.
firebaseAppDistributionDefault {
    artifactType = "APK"
    groups = "testers"
    releaseNotesFile = "release-notes.txt"
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Firebase is used for one thing only: shipping new versions to testers.
    // The API artifact is a no-op stub; the full SDK is what actually performs the update.
    implementation(libs.firebase.appdistribution.api)
    implementation(libs.firebase.appdistribution)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
