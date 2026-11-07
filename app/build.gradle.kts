import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.carbonwise"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.carbonwise"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Secrets come from local.properties (git-ignored), never from source code
        val localProps = Properties()
        rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { localProps.load(it) }
        buildConfigField("String", "ELECTRICITY_MAPS_TOKEN", "\"${localProps.getProperty("electricitymaps.token", "")}\"")
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // Enable View Binding for cleaner UI interaction
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)

    // Google Sign-In Legacy/Standard API
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // Map: MapLibre with free OpenFreeMap vector tiles (no API key or billing needed)
    implementation("org.maplibre.gl:android-sdk:11.8.2")

    implementation(libs.firebase.auth)
    implementation(libs.googleid)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)

    // Cloud Firestore for cloud persistence and sync
    implementation("com.google.firebase:firebase-firestore:25.0.0")

    // Retrofit & Gson Converter (REST API & JSON Parsing)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)

    // WorkManager (Background Sync & Daily Reminders)
    implementation(libs.work.runtime)
}