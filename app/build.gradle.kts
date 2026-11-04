plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "uk.me.g4dpz.HamSatDroid"
    compileSdk = 36

    defaultConfig {
        applicationId = "uk.me.g4dpz.HamSatDroid"
        minSdk = 30
        targetSdk = 36
        versionCode = 17
        versionName = "1.0.14"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.txt")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    // Compose: the BOM fixes the versions of the androidx.compose.* libraries together.
    // Versions are deliberately one step behind the newest stable: Compose 1.12, lifecycle 2.11, navigation 2.10 and
    // activity 1.13 need compileSdk 37 and AGP 9.1+, while this project is on compileSdk 36 and AGP 8.13.
    // Move all of them (and compileSdk/AGP) together when upgrading.
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")

    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    testImplementation("junit:junit:4.13.2")
    // Android's org.json is only a stub on the JVM, so unit tests need the real implementation.
    testImplementation("org.json:json:20260814")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}
