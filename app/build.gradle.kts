plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Version code = the CI run number, so every build installs over the last one.
val vc = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
// Release builds are signed with the Tandem Phone key when CI provides it (same key every build, so updates install).
val ks = System.getenv("TP_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }

android {
    namespace = "app.tandem.phone"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.tandem.phone"
        minSdk = 28
        targetSdk = 35
        versionCode = vc
        versionName = "1.0.$vc"
    }

    signingConfigs {
        create("tandem") {
            if (ks != null) {
                storeFile = ks
                storePassword = System.getenv("TP_STORE_PASS")
                keyAlias = System.getenv("TP_KEY_ALIAS") ?: "tandem"
                keyPassword = System.getenv("TP_KEY_PASS") ?: System.getenv("TP_STORE_PASS")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (ks != null) signingConfigs.getByName("tandem") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.health.connect:connect-client:1.1.0")
}
