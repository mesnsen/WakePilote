plugins { id("com.android.library"); kotlin("android") }
android {
    namespace = "io.github.mesnsen.wakepilote.vision"; compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":core"))
    api("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.google.mediapipe:tasks-vision:0.10.26.1")
}
