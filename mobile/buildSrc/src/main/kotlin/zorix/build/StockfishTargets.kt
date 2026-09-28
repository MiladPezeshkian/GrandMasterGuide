package zorix.build

import java.io.File
import java.io.Serializable

/**
 * One Stockfish executable to build.
 *
 * The file is named `lib*.so` so that Android packages it into the APK's native library
 * directory, the only app-owned location from which Android 10+ allows executing a binary.
 */
data class StockfishTarget(
    /** Android ABI folder, e.g. "arm64-v8a". */
    val abi: String,
    /** Output file name inside the ABI folder. */
    val fileName: String,
    /** Clang target triple including the API level, e.g. "aarch64-linux-android26". */
    val triple: String,
    /** Architecture specific compiler flags (mirrors Stockfish's Makefile ARCH settings). */
    val flags: List<String>,
    /** Stockfish ARCH name, shown by the engine in its build info. */
    val arch: String,
) : Serializable {
    val id: String get() = "$abi-$arch"
}

object StockfishTargets {

    val SUPPORTED_ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86_64")

    /** Build variants for [abi]. arm64 gets an extra ARMv8.2 dot-product build picked at runtime. */
    fun forAbi(abi: String, api: Int): List<StockfishTarget> = when (abi) {
        "arm64-v8a" -> listOf(
            StockfishTarget(
                abi, "libstockfish.so", "aarch64-linux-android$api",
                listOf("-DIS_64BIT", "-DUSE_POPCNT", "-DUSE_NEON=8"),
                "armv8",
            ),
            StockfishTarget(
                abi, "libstockfish_dotprod.so", "aarch64-linux-android$api",
                listOf("-DIS_64BIT", "-DUSE_POPCNT", "-DUSE_NEON=8", "-march=armv8.2-a+dotprod", "-DUSE_NEON_DOTPROD"),
                "armv8-dotprod",
            ),
        )
        "armeabi-v7a" -> listOf(
            StockfishTarget(
                abi, "libstockfish.so", "armv7a-linux-androideabi$api",
                listOf("-mthumb", "-march=armv7-a", "-mfloat-abi=softfp", "-mfpu=neon", "-DUSE_POPCNT", "-DUSE_NEON=7"),
                "armv7-neon",
            ),
        )
        // The Android x86_64 ABI guarantees SSE4.2 and POPCNT, so this is safe on every device/emulator.
        "x86_64" -> listOf(
            StockfishTarget(
                abi, "libstockfish.so", "x86_64-linux-android$api",
                listOf(
                    "-DIS_64BIT", "-msse", "-msse2", "-msse3", "-mssse3", "-msse4.1", "-mpopcnt",
                    "-DUSE_POPCNT", "-DUSE_SSE2", "-DUSE_SSSE3", "-DUSE_SSE41",
                ),
                "x86-64-sse41-popcnt",
            ),
        )
        else -> throw IllegalArgumentException(
            "Stockfish: unsupported ABI '$abi'. Supported: ${SUPPORTED_ABIS.joinToString()}",
        )
    }

    /** Compiler flags shared by every target (from Stockfish's Makefile, clang + optimize=yes). */
    val COMMON_FLAGS = listOf(
        "-std=c++17", "-O3", "-funroll-loops", "-fno-exceptions", "-DNDEBUG",
        "-Wall", "-Wcast-qual", "-fPIE", "-flto=full",
        // The network ships as an app asset (one copy for all ABIs) instead of being embedded 4 times.
        "-DNNUE_EMBEDDING_OFF",
    )

    val LINK_FLAGS = listOf(
        "-static-libstdc++", "-fPIE", "-pie", "-s",
        // 16 KB page size compatibility (required by Google Play for Android 15+ devices).
        "-Wl,-z,max-page-size=16384",
    )

    /**
     * Finds an installed NDK: `<sdk>/ndk/<preferredVersion>`, otherwise the newest NDK r25+ in `<sdk>/ndk`,
     * `ANDROID_NDK_HOME`, or the legacy `<sdk>/ndk-bundle`.
     */
    fun findNdk(sdkDir: File, preferredVersion: String): File {
        fun usable(dir: File?) = dir != null && dir.isDirectory && runCatching { ndkClang(dir) }.isSuccess
        val ndkRoot = File(sdkDir, "ndk")
        File(ndkRoot, preferredVersion).takeIf(::usable)?.let { return it }
        val installed = ndkRoot.listFiles().orEmpty()
            .filter { (it.name.substringBefore('.').toIntOrNull() ?: 0) >= 25 && usable(it) }
            .sortedWith(compareBy<File>({ it.name.substringBefore('.').toIntOrNull() ?: 0 }, { it.name }))
        installed.lastOrNull()?.let { return it }
        System.getenv("ANDROID_NDK_HOME")?.let(::File)?.takeIf(::usable)?.let { return it }
        File(sdkDir, "ndk-bundle").takeIf(::usable)?.let { return it }
        throw IllegalStateException(
            "Stockfish: the Android NDK is not installed. In Android Studio open Settings > Languages & Frameworks > " +
                "Android SDK > SDK Tools, tick \"Show Package Details\", select NDK (Side by side) $preferredVersion and click Apply.",
        )
    }

    /** Locates clang++ inside an NDK installation on Windows, macOS or Linux. */
    fun ndkClang(ndkDir: File): File {
        val prebuilt = File(ndkDir, "toolchains/llvm/prebuilt")
        val hosts = prebuilt.listFiles()?.filter { it.isDirectory }.orEmpty()
        val exe = if (System.getProperty("os.name").lowercase().contains("windows")) "clang++.exe" else "clang++"
        val clang = hosts.map { File(it, "bin/$exe") }.firstOrNull { it.isFile }
        return clang ?: throw IllegalStateException(
            "Stockfish: clang++ was not found in the NDK at $ndkDir. " +
                "Install the NDK from Android Studio > Settings > Languages & Frameworks > Android SDK > SDK Tools > NDK (Side by side).",
        )
    }

    /** The NDK sysroot that sits next to clang's bin folder. */
    fun ndkSysroot(clang: File): File = File(clang.parentFile.parentFile, "sysroot")

    /** Reads the `SRCS = ...` list from Stockfish's Makefile so updating the engine sources needs no build changes. */
    fun sourcesFromMakefile(srcDir: File): List<String> {
        val makefile = File(srcDir, "Makefile")
        if (makefile.isFile) {
            val text = makefile.readText().replace("\\\r\n", " ").replace("\\\n", " ")
            val line = text.lineSequence().firstOrNull { it.trimStart().startsWith("SRCS") && it.contains("=") }
            if (line != null) {
                val files = line.substringAfter("=").trim().split(Regex("\\s+")).filter { it.endsWith(".cpp") }
                if (files.isNotEmpty() && files.all { File(srcDir, it).isFile }) return files
            }
        }
        // Fallback: every translation unit except the macOS/universal-binary helpers.
        return srcDir.walkTopDown()
            .filter { it.isFile && it.extension == "cpp" }
            .map { it.relativeTo(srcDir).invariantSeparatorsPath }
            .filterNot { it.startsWith("universal/") || it.startsWith("incbin/") }
            .sorted()
            .toList()
    }

    /** `#define EvalFileDefaultName... "nn-xxxxxxxxxxxx.nnue"` entries from evaluate.h. */
    fun networkNames(evaluateHeader: File): List<String> {
        val regex = Regex("""#define\s+EvalFileDefaultName\w*\s+"(nn-[0-9a-f]{12}\.nnue)"""")
        return regex.findAll(evaluateHeader.readText()).map { it.groupValues[1] }.distinct().toList()
    }
}
