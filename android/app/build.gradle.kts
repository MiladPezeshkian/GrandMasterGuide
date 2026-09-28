import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import zorix.build.BuildStockfishTask
import zorix.build.DownloadStockfishNetTask
import zorix.build.StockfishTargets

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/** Android version the app (and the Stockfish executable) targets at minimum: Android 8.0. */
val minApi = 26

/** API level the engine is compiled for (defaults to minSdk). */
val engineApi: Int = providers.gradleProperty("zorix.stockfish.api").orNull?.toIntOrNull() ?: minApi

/** NDK used for Stockfish (any installed r25+ is used if this exact version is missing). */
val stockfishNdkVersion: String = providers.gradleProperty("zorix.ndkVersion").getOrElse("27.2.12479018")

val stockfishAbis: List<String> = providers.gradleProperty("zorix.stockfish.abis").orNull
    ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
    ?: StockfishTargets.SUPPORTED_ABIS

android {
    namespace = "com.zorix.chess"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zorix.chess"
        minSdk = minApi
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        ndk { abiFilters += stockfishAbis }
    }

    signingConfigs {
        val keystore = providers.gradleProperty("zorix.keystore").orNull
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = providers.gradleProperty("zorix.keystorePassword").orNull
                keyAlias = providers.gradleProperty("zorix.keyAlias").orNull
                keyPassword = providers.gradleProperty("zorix.keyPassword").orNull
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Use your own key when configured in gradle.properties, otherwise the debug key so the APK installs.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            // Stockfish is an executable: it must be extracted to the native library folder to run.
            useLegacyPackaging = true
            keepDebugSymbols += "**/libstockfish*.so"
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// ---------------------------------------------------------------------------------------------
// Stockfish: compiled from ../stockfish/src with the NDK, plus its neural network as an asset.
// ---------------------------------------------------------------------------------------------

val ndkClang = androidComponents.sdkComponents.sdkDirectory.map { sdk ->
    StockfishTargets.ndkClang(StockfishTargets.findNdk(sdk.asFile, stockfishNdkVersion))
}

val buildStockfish = tasks.register<BuildStockfishTask>("buildStockfish") {
    group = "zorix"
    description = "Compiles the Stockfish chess engine for ${stockfishAbis.joinToString()}."
    sourceDir.set(rootProject.layout.projectDirectory.dir("stockfish/src"))
    compiler.set(ndkClang.map { it.absolutePath })
    sysroot.set(ndkClang.map { StockfishTargets.ndkSysroot(it).absolutePath })
    targets.set(stockfishAbis.flatMap { StockfishTargets.forAbi(it, engineApi) })
    compilerFlags.set(StockfishTargets.COMMON_FLAGS)
    linkerFlags.set(StockfishTargets.LINK_FLAGS)
    outputDir.set(layout.buildDirectory.dir("generated/stockfish/jniLibs"))
    objectDir.set(layout.buildDirectory.dir("intermediates/stockfish/obj"))
}

val downloadStockfishNet = tasks.register<DownloadStockfishNetTask>("downloadStockfishNet") {
    group = "zorix"
    description = "Provides Stockfish's NNUE network as an app asset (downloaded once, cached in stockfish/nets)."
    evaluateHeader.set(rootProject.layout.projectDirectory.file("stockfish/src/evaluate.h"))
    cacheDir.set(rootProject.layout.projectDirectory.dir("stockfish/nets"))
    outputDir.set(layout.buildDirectory.dir("generated/stockfish/assets"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(buildStockfish, BuildStockfishTask::outputDir)
        variant.sources.assets?.addGeneratedSourceDirectory(downloadStockfishNet, DownloadStockfishNetTask::outputDir)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
