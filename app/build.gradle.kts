import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "de.lernapp.ihk"
    compileSdk = 37

    defaultConfig {
        applicationId = "de.lernapp.ihk"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Privat-App: mit dem Debug-Schlüssel signiert, damit die APK direkt installierbar ist.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        // LiteRT-LM kann mit einer neueren Kotlin-Version gebaut sein (wie in Googles Gallery-App).
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Lokale KI: LiteRT-LM (Google AI Edge) für .litertlm-Modelle wie Gemma 4
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.17.1")

    testImplementation("junit:junit:4.13.2")
}
