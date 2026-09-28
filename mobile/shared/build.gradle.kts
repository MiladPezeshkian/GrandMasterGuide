import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

/** Stockfish + bridge as a static library for iOS devices (built by iosApp/stockfish/build_ios.sh on macOS). */
val stockfishIosDir = layout.buildDirectory.dir("stockfish-ios")
val buildStockfishIos = tasks.register<Exec>("buildStockfishIos") {
    group = "zorix"
    description = "Compiles Stockfish for iOS (arm64) with the network embedded."
    val script = rootProject.file("iosApp/stockfish/build_ios.sh")
    commandLine("bash", script.absolutePath, stockfishIosDir.get().asFile.absolutePath)
    inputs.dir(rootProject.file("stockfish/src"))
    inputs.file(rootProject.file("iosApp/stockfish/zorix_stockfish.cpp"))
    inputs.file(script)
    outputs.file(stockfishIosDir.map { it.file("libzorixstockfish.a") })
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    iosArm64 {
        compilations.getByName("main").cinterops.create("stockfish") {
            definitionFile.set(project.file("src/nativeInterop/cinterop/stockfish.def"))
            includeDirs(rootProject.file("iosApp/stockfish"))
            extraOpts("-libraryPath", stockfishIosDir.get().asFile.absolutePath)
        }
        binaries.framework {
            baseName = "Shared"
            isStatic = false
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.animation)
            implementation(compose.ui)
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.atomicfu)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.kotlinx.coroutines.android)
        }
        getByName("androidUnitTest").dependencies {
            implementation(libs.junit)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

tasks.matching { it.name.startsWith("cinteropStockfish") }.configureEach { dependsOn(buildStockfishIos) }

android {
    namespace = "com.zorix.chess.shared"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "com.zorix.chess.resources"
    generateResClass = always
}
