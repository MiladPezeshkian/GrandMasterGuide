// Top-level build file. Module configuration lives in app/build.gradle.kts,
// the Stockfish native build logic in buildSrc/.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
