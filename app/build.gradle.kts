import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Central configuration: repo-root app-config.properties (public values only).
// An environment variable named EV_<KEY> overrides the file (useful for CI).
val appConfig = Properties().apply {
    val f = rootProject.file("app-config.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun cfg(key: String): String =
    (System.getenv("EV_$key") ?: appConfig.getProperty(key) ?: "REPLACE_ME").trim()

fun quoted(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.educationvault.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.educationvault.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "FIREBASE_API_KEY", quoted(cfg("FIREBASE_API_KEY")))
        buildConfigField("String", "FIREBASE_PROJECT_ID", quoted(cfg("FIREBASE_PROJECT_ID")))
        buildConfigField("String", "APPS_SCRIPT_URL", quoted(cfg("APPS_SCRIPT_URL")))
        val maxBytes = cfg("MAX_UPLOAD_BYTES").toLongOrNull() ?: 5242880L
        buildConfigField("long", "MAX_UPLOAD_BYTES", "${maxBytes}L")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
