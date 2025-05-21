plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.kbpro"
    compileSdk = 34

    defaultConfig {
        // For WearOS and Android phones to communicate over the data layer, they have to have
        // the same application id.
        applicationId = "com.example.kbpro"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    
    defaultConfig.manifestPlaceholders["wearableApp"] = true

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"  // Compatible with Kotlin 1.9.22
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("app/src/main/AndroidManifest.xml")
            java.srcDirs("app/src/main/java")
            res.srcDirs("app/src/main/res")
            assets.srcDirs("app/src/main/assets")
        }
    }

    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs += listOf("-P", "plugin:androidx.compose.compiler.plugins.kotlin:suppressKotlinVersionCompatibilityCheck=true")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.wear:wear:1.3.0")
    implementation("androidx.wear.compose:compose-material:1.2.1")
    implementation("androidx.wear.compose:compose-foundation:1.2.1")
    implementation("androidx.wear.tiles:tiles:1.2.0")
    implementation("androidx.wear.tiles:tiles-material:1.2.0")
    implementation("com.google.android.horologist:horologist-compose-tools:0.5.19")
    implementation("com.google.android.horologist:horologist-tiles:0.5.19")
    implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.1.1")
    implementation("androidx.compose.ui:ui-tooling-preview:1.5.4")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("com.google.android.gms:play-services-wearable:18.1.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.5.4")
}
