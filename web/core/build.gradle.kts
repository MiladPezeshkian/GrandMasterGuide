/*
 * The app's chess logic (rules, coach, lessons, puzzles, bots, game review) compiled to JavaScript for
 * the website, so the site explains and teaches exactly like the app. The sources are the app's own
 * (mobile/shared/src/commonMain), without the Compose UI.
 *
 *   gradle -p web/core build        (writes web/src/core/zorix-core.mjs; run after changing the app logic)
 */
plugins {
    kotlin("multiplatform") version "2.0.21"
}

val shared = rootDir.resolve("../../mobile/shared/src/commonMain/kotlin")
val packages = listOf("core", "coach", "engine", "learn", "play", "controller")

kotlin {
    js(IR) {
        moduleName = "zorix-core"
        browser()
        binaries.library()
        useEsModules()
        generateTypeScriptDefinitions()
        compilerOptions { target.set("es2015") }
    }
    sourceSets {
        commonMain {
            kotlin.srcDir(shared)
            // The app's platform/Platform.kt (with Compose) is replaced by src/commonMain's WebPlatform.kt.
            kotlin.include(packages.map { "com/zorix/chess/$it/**" } + "com/zorix/chess/platform/WebPlatform.kt")
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("org.jetbrains.kotlinx:atomicfu:0.26.1")
            }
        }
    }
}

// The browser bundle is built by Next.js; Gradle only compiles the library, with the system Node.js.
rootProject.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin> {
    rootProject.the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension>().download = false
}
rootProject.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
    rootProject.the<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension>().download = false
}

val copyToWeb = tasks.register<Copy>("copyToWeb") {
    dependsOn("jsProductionLibraryCompileSync")
    from(layout.buildDirectory.dir("js/packages/zorix-core/kotlin"))
    include("*.mjs", "*.d.ts")
    into(rootDir.resolve("../src/core"))
}
tasks.named("build") { finalizedBy(copyToWeb) }
