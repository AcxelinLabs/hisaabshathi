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
        versionCode = 6
        versionName = "0.5.1"
        // Where phones download the fine-tuned model from (public, Apache-2.0).
        buildConfigField(
            "String", "HISAAB_MODEL_URL",
            "\"https://huggingface.co/QubitMan/hisaabsaathi-model/resolve/main/hisaabsaathi-qwen05-ft-q8.task\"",
        )
    }

    androidResources { noCompress += "task" }

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
    kotlinOptions { jvmTarget = "17" }
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
        jniLibs.excludes += listOf("**/libsherpa-onnx-c-api.so", "**/libsherpa-onnx-cxx-api.so", "**/libonnxruntime4j_jni.so")
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
    implementation(libs.mediapipe.genai)   // on-device small language model
    implementation(libs.mlkit.text.devanagari) // on-device OCR (Devanagari + Latin)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.coroutines.test)
    debugImplementation(libs.compose.ui.test.manifest)
}
