import java.security.MessageDigest

plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "io.github.mesnsen.wakepilote"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.mesnsen.wakepilote"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.1.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // MediaPipe 0.10.26.1 does not ship x86_64. Do not advertise a partial ABI.
        ndk { abiFilters += "arm64-v8a" }
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildTypes { release { isMinifyEnabled = false } }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    androidResources { noCompress += "task" }
}
val verifyFaceModel by tasks.registering {
    val model = layout.projectDirectory.file("src/main/assets/face_landmarker.task")
    inputs.file(model)
    doLast {
        val digest = MessageDigest.getInstance("SHA-256").digest(model.asFile.readBytes()).joinToString("") { "%02x".format(it) }
        check(digest == "64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff") { "Modèle facial absent ou hash incorrect" }
    }
}
tasks.named("preBuild") { dependsOn(verifyFaceModel) }
dependencies {
    implementation(project(":core")); implementation(project(":vision"))
    implementation(project(":audio")); implementation(project(":data"))
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("com.google.mediapipe:tasks-vision:0.10.26.1")
}
