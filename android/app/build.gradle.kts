plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.acxelin.hisaabsaathi"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.acxelin.hisaabsaathi"
        minSdk = 29 // Android 10
        targetSdk = 35
        versionCode = 20
        versionName = "0.9.2"
        // Where phones download the fine-tuned model from (public, Apache-2.0).
        buildConfigField(
            "String", "HISAAB_MODEL_URL",
            "\"https://huggingface.co/AcxelinLabs/hisaabsaathi-model/resolve/main/hisaabsaathi-qwen3-ft-q8.litertlm\"",
        )
        // The team's upload service for "Help improve HisaabSaathi" (server/ in this repository),
        // e.g. ./gradlew assembleRelease -Phisaab.shareUrl=https://improve.example.org
        // Empty: what workers agree to share waits on the phone until a build has one.
        buildConfigField("String", "SHARE_URL", "\"${providers.gradleProperty("hisaab.shareUrl").getOrElse("")}\"")
        // The team's public key (base64, from `python -m hisaab_improve.crypto new-key`). Batches are
        // sealed to it on the phone; only the training pipeline holds the private key. Both are needed to send.
        buildConfigField("String", "SHARE_KEY", "\"${providers.gradleProperty("hisaab.shareKey").getOrElse("")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signed with the debug key until the team adds its own upload key.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }

    // One APK per processor type keeps each download small (most phones need arm64-v8a).
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Save a screenshot of every screen the flow test visits to app/build/screens.
        unitTests.all { it.systemProperty("roborazzi.test.record", "true") }
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        jniLibs.useLegacyPackaging = false
        // Parts of sherpa-onnx the app does not call.
        jniLibs.excludes += listOf("**/libsherpa-onnx-c-api.so", "**/libsherpa-onnx-cxx-api.so")
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coroutines.android)

    implementation(libs.datastore)
    implementation(libs.work)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher)
    implementation(libs.sqlite)

    implementation(libs.sherpa.onnx)       // on-device speech-to-text + voice activity detection
    // ONNX Runtime 1.29 for the voices. Its native library is shipped renamed (libortvoice.so,
    // src/main/jniLibs) so it cannot clash with the 1.28.2 copy inside sherpa-onnx.
    implementation(files("libs/onnxruntime-android-1.29.0-classes.jar"))
    implementation(libs.litertlm)          // on-device small language model (LiteRT-LM)
    implementation(libs.mlkit.text.devanagari) // on-device OCR (Devanagari + Latin)
    implementation(libs.mlkit.barcode)         // Aadhaar QR from a saved photo, on the phone
    implementation(libs.camerax.camera2)       // live Aadhaar QR scanner inside the app (CameraX + the bundled ML Kit reader)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.coroutines.test)
    debugImplementation(libs.compose.ui.test.manifest)
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
