import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing is read from a gitignored keystore.properties in the project root (see
// keystore.properties.template). When that file is absent — a fresh clone, or a CI machine without the key — the
// release build is simply left unsigned instead of failing, so debug builds and CI are unaffected.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseSigning = keystoreProperties.getProperty("storeFile") != null

android {
    namespace = "uk.me.g4dpz.HamSatDroid"
    compileSdk = 36

    defaultConfig {
        applicationId = "uk.me.g4dpz.amsatdroid"
        minSdk = 30
        targetSdk = 36
        versionCode = 23
        versionName = "2.0.3"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Build the en-XA / ar-XB pseudolocales so a string audit can switch to them; debug only.
            isPseudoLocalesEnabled = true
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.txt")
            // Use the release signing config when keystore.properties is present; otherwise leave the build unsigned.
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        // Lint errors fail the build; a check that cannot fail is not a check. Warnings are still only reported.
        abortOnError = true
    }

    testOptions {
        unitTests {
            // Robolectric needs the resources and the merged manifest. The plain JVM tests are unaffected: only
            // classes that name the Robolectric runner use it.
            isIncludeAndroidResources = true
        }
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
    // Home-screen widget: Glance (Compose-style app widgets). 1.2.0 is the current stable.
    implementation("androidx.glance:glance-appwidget:1.2.0")
    implementation("androidx.glance:glance-material3:1.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    testImplementation("junit:junit:4.13.2")
    // Android's org.json is only a stub on the JVM, so unit tests need the real implementation.
    testImplementation("org.json:json:20260814")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    // Robolectric: Android framework on the JVM, for the receivers, scheduler and notifier.
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.7.0")
    // WorkManager test initialiser: the application onCreate schedules work, so tests need an instance first.
    testImplementation("androidx.work:work-testing:2.12.0")
}
