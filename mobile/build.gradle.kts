// Top-level build file.
//   shared/      Kotlin Multiplatform: chess rules, engine protocol, app logic and the Compose UI (Android + iOS)
//   androidApp/  Android application (Stockfish compiled with the NDK, see buildSrc/)
//   iosApp/      iOS application (Xcode project generated from project.yml)
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
