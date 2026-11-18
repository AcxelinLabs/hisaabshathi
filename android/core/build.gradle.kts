plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}
kotlin { jvmToolchain(21)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(libs.serialization.json)
    // ONNX Runtime API for the voices (TinyTts). The app supplies the Android build of it.
    compileOnly("com.microsoft.onnxruntime:onnxruntime:1.29.0")
    testImplementation("com.microsoft.onnxruntime:onnxruntime:1.29.0")
    testImplementation(libs.junit)
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
