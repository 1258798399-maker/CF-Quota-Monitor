import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Load signing credentials from app/keystore.properties (gitignored).
// When the file is missing (e.g. an open-source clone without a personal
// keystore), the release config below is simply not populated and Gradle
// will fall back to the debug signing config.
val keystorePropertiesFile = rootProject.file("app/keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

// Glance 1.1.1 transitively pulls WorkManager 2.7.1. On targetSdk >= 34 (we
// are 36) that old release crashes at startup because its foreground service
// lacks a foregroundServiceType declaration. Force the current, API-34+-safe
// release so the app can launch at all.
configurations.all {
    resolutionStrategy {
        force("androidx.work:work-runtime-ktx:2.10.0")
    }
}

android {
    namespace = "com.nova.cfquota"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nova.cfquota"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "1.6.3"

        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            // Signing credentials come from app/keystore.properties (gitignored).
            // Copy `keystore.properties.example` and fill in your own values to
            // produce a self-signable release APK. See README for details.
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    // Core / Lifecycle / Activity
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Compose UI + Material3
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // OkHttp (direct, not via Ktor) — for full control over caching, connection
    // pool, and interceptors. Ktor 3.0.3 + OkHttp engine is kept for safety
    // (Kotlinx serialization plugin still uses it for ContentNegotiation).
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // DataStore (encrypted values via Android Keystore helper)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // WorkManager (explicit; Glance only pulls it transitively) — drives the
    // background periodic auto-refresh worker. The resolutionStrategy.force above
    // keeps it pinned to the API-34+-safe 2.10.0 release.
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Glance App Widget
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
