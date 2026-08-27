import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.navigation.safeargs)
    id("kotlin-parcelize")
}

val keystorePropertiesFile = rootProject.file("key.properties")
val keystoreProperties = Properties()
val hasReleaseKeystore = keystorePropertiesFile.isFile

if (hasReleaseKeystore) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

fun requireKeystoreProperty(name: String): String {
    return keystoreProperties.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }
        ?: error("key.properties is missing or empty: $name")
}

android {
    namespace = "com.manikandan.tripoo"
    compileSdk = 36

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                keyAlias = requireKeystoreProperty("keyAlias")
                keyPassword = requireKeystoreProperty("keyPassword")
                storePassword = requireKeystoreProperty("storePassword")
                val store = rootProject.file(requireKeystoreProperty("storeFile"))
                if (!store.isFile) {
                    error("Keystore not found: ${store.absolutePath}")
                }
                storeFile = store
            }
        }
    }

    defaultConfig {
        applicationId = "com.manikandan.tripoo"
        minSdk = 24
        targetSdk = 36
        versionCode = 14
        versionName = "2.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // AndroidX Core / UI
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)

    // Local trip alerts (Firestore fan-out + WorkManager); no FCM server / no Blaze Functions.
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Navigation Component
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    // Lifecycle & ViewModel
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.lifecycle.runtime)

    // Glide
    implementation(libs.glide)

    // AdMob
    implementation(libs.play.services.ads)

    // Play Store: detect when a newer version is available on Play
    implementation("com.google.android.play:app-update:2.1.0")
    implementation("com.google.android.play:app-update-ktx:2.1.0")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

listOf("assembleRelease", "bundleRelease").forEach { taskName ->
    tasks.matching { it.name == taskName }.configureEach {
        doFirst {
            if (!hasReleaseKeystore) {
                throw GradleException(
                    """
                    |Release signing is not configured.
                    |1. Copy key.properties.example → key.properties
                    |2. Put your Play upload keystore in the project root (see KEYSTORE_README.md)
                    |3. Fill in storeFile, storePassword, keyAlias, keyPassword
                    |4. Run: ./gradlew :app:bundleRelease
                    """.trimMargin(),
                )
            }
        }
    }
}
