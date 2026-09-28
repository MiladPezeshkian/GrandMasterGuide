package com.zorix.chess.engine

import android.content.Context
import android.os.Build
import com.zorix.chess.controller.EngineHost
import com.zorix.chess.controller.EngineLaunch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Runs the Stockfish executable that Gradle compiled into the APK's native library folder
 * (`libstockfish.so`, plus `libstockfish_dotprod.so` for ARMv8.2+ phones) and installs its
 * neural network from the APK assets into private storage on first launch.
 * No network access is ever needed.
 */
class AndroidEngineHost(context: Context) : EngineHost {

    private val app = context.applicationContext

    override val cpuCores: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

    override suspend fun prepare(onProgress: (Float) -> Unit): List<EngineLaunch> = withContext(Dispatchers.IO) {
        // no_backup: the ~100 MB network must not be uploaded by Android's auto backup.
        val netDir = File(app.noBackupFilesDir, "nnue")
        installNetworks(netDir, onProgress)

        val libDir = File(app.applicationInfo.nativeLibraryDir)
        val launches = ArrayList<EngineLaunch>()
        val dotProd = File(libDir, "libstockfish_dotprod.so")
        if (dotProd.isFile && cpuSupportsDotProduct()) {
            launches += EngineLaunch(listOf(dotProd.absolutePath), netDir, "armv8.2 dotprod")
        }
        val base = File(libDir, "libstockfish.so")
        if (base.isFile) launches += EngineLaunch(listOf(base.absolutePath), netDir, baseLabel())
        if (launches.isEmpty()) throw IOException("Stockfish was not found in $libDir")
        launches
    }

    /** Copies assets/nnue/\*.nnue to [dir] unless an identical copy is already there. */
    private fun installNetworks(dir: File, onProgress: (Float) -> Unit) {
        val assets = app.assets
        val names = assets.list("nnue")?.filter { it.endsWith(".nnue") }.orEmpty()
        if (names.isEmpty()) throw IOException("The Stockfish neural network is missing from the app (assets/nnue)")
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Cannot create $dir")

        val sizes = names.associateWith { name -> assets.open("nnue/$name").use { it.available().toLong() } }
        val missing = names.filter { File(dir, it).length() != sizes.getValue(it) }
        // Remove networks of older app versions.
        dir.listFiles()?.filter { it.name !in names }?.forEach { it.delete() }
        if (missing.isEmpty()) {
            onProgress(1f)
            return
        }

        val total = missing.sumOf { sizes.getValue(it) }.coerceAtLeast(1)
        var copied = 0L
        var lastReported = -1
        onProgress(0.001f)
        for (name in missing) {
            val target = File(dir, name)
            val temp = File(dir, "$name.tmp")
            assets.open("nnue/$name").use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(1 shl 18)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        copied += n
                        val percent = (copied * 100 / total).toInt()
                        if (percent != lastReported) {
                            lastReported = percent
                            onProgress(copied.toFloat() / total)
                        }
                    }
                }
            }
            if (!temp.renameTo(target)) {
                temp.delete()
                throw IOException("Could not install $name")
            }
        }
        onProgress(1f)
    }

    /** ARMv8.2 dot-product instructions make the NNUE evaluation noticeably faster. */
    private fun cpuSupportsDotProduct(): Boolean = try {
        File("/proc/cpuinfo").readLines()
            .filter { it.startsWith("Features", ignoreCase = true) }
            .any { line -> line.substringAfter(':').split(' ').contains("asimddp") }
    } catch (_: Exception) {
        false
    }

    private fun baseLabel(): String = when (Build.SUPPORTED_ABIS.firstOrNull()) {
        "arm64-v8a" -> "armv8"
        "armeabi-v7a" -> "armv7 neon"
        "x86_64" -> "x86-64 sse4.1"
        else -> Build.SUPPORTED_ABIS.firstOrNull() ?: "native"
    }
}
