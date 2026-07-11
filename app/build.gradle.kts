plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.music.spotui"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.music.spotui"
        minSdk = 26
        targetSdk = 34
        versionCode = 134
        versionName = "1.3.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("sharedDebug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "hazhan"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Sign release with the shared debug key so the APK is installable via sideload
            // and upgrades the existing (debug-signed) install in place.
            signingConfig = signingConfigs.getByName("sharedDebug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("sharedDebug")
        }
    }

    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.ApkVariantOutputImpl
            output.outputFileName = "Spotui_v${versionName}.apk"
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.compose.runtime:runtime-livedata:1.6.8")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // Spotify metadata + YouTube streaming, ported from Meld (replaces Firebase data layer)
    implementation(project(":spotify"))
    implementation(project(":innertube"))
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("androidx.navigation:navigation-compose:2.7.7")

    //hilt
    implementation("com.google.dagger:hilt-android:2.57.1")
    ksp("com.google.dagger:hilt-android-compiler:2.57.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    //coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    //await
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    //glide
    implementation("com.github.bumptech.glide:compose:1.0.0-beta01")

    //splashScreen
    implementation("androidx.core:core-splashscreen:1.0.1")

    //palette
    implementation("androidx.palette:palette-ktx:1.0.0")

    //exoplayer
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.3.1")
    // PlayerView for the Spotify Canvas looping video on the now-playing screen.
    implementation("androidx.media3:media3-ui:1.3.1")
    // media session + system media notification (lock screen / notification center)
    implementation("androidx.media3:media3-session:1.3.1")

    //okhttp + timber (used by the ported YouTube streaming flow)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    //core library desugaring (required by :innertube)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
}