package zorix.build

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

/**
 * Provides the NNUE network file(s) Stockfish needs, as app assets under `nnue/`.
 *
 * The network is looked up in [cacheDir] (android/stockfish/nets, kept between clean builds)
 * and downloaded only once if missing. Its SHA-256 is checked against the file name, as
 * Stockfish itself does. The finished app never needs the internet: the net is inside the APK.
 */
abstract class DownloadStockfishNetTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val evaluateHeader: RegularFileProperty

    @get:Internal
    abstract val cacheDir: DirectoryProperty

    /** Generated assets root; files land in `nnue/` inside it. */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun provide() {
        val names = StockfishTargets.networkNames(evaluateHeader.get().asFile)
        if (names.isEmpty()) throw GradleException("Stockfish: no EvalFileDefaultName found in evaluate.h")
        val cache = cacheDir.get().asFile.apply { mkdirs() }
        val assetDir = File(outputDir.get().asFile, "nnue")
        assetDir.deleteRecursively()
        assetDir.mkdirs()

        for (name in names) {
            val cached = File(cache, name)
            if (!isValid(cached, name)) download(name, cached)
            cached.copyTo(File(assetDir, name), overwrite = true)
            logger.lifecycle("Stockfish: network $name ready (${cached.length() / (1024 * 1024)} MB)")
        }
    }

    private fun download(name: String, dest: File) {
        val urls = listOf(
            "https://tests.stockfishchess.org/api/nn/$name",
            "https://github.com/official-stockfish/networks/raw/master/$name",
            "https://media.githubusercontent.com/media/official-stockfish/networks/master/$name",
        )
        val failures = mutableListOf<String>()
        for (url in urls) {
            val part = File(dest.parentFile, "$name.part")
            try {
                logger.lifecycle("Stockfish: downloading $name from $url (one-time, about 100 MB)...")
                fetch(url, part)
                if (!isValid(part, name)) {
                    failures += "$url: checksum mismatch"
                    part.delete()
                    continue
                }
                if (dest.exists()) dest.delete()
                if (!part.renameTo(dest)) {
                    part.copyTo(dest, overwrite = true)
                    part.delete()
                }
                return
            } catch (e: Exception) {
                failures += "$url: ${e.message}"
                part.delete()
            }
        }
        throw GradleException(
            buildString {
                appendLine("Stockfish: could not download the neural network $name.")
                failures.forEach { appendLine("  - $it") }
                appendLine("Download it manually from https://tests.stockfishchess.org/api/nn/$name")
                appendLine("(or https://github.com/official-stockfish/networks) and place it in:")
                append("  ").append(dest.parentFile.absolutePath)
            },
        )
    }

    private fun fetch(url: String, dest: File) {
        var current = URI(url)
        var connection: HttpURLConnection
        var redirects = 0
        while (true) {
            connection = current.toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 30_000
            connection.readTimeout = 120_000
            connection.setRequestProperty("User-Agent", "ZorixChess-build")
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location") ?: throw GradleException("redirect without location")
                current = current.resolve(location)
                connection.disconnect()
                if (++redirects > 5) throw GradleException("too many redirects")
                continue
            }
            if (code != 200) throw GradleException("HTTP $code")
            break
        }
        val total = connection.contentLengthLong
        connection.inputStream.use { input ->
            dest.outputStream().use { output ->
                val buffer = ByteArray(1 shl 16)
                var done = 0L
                var nextReport = 10L shl 20
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    done += n
                    if (done >= nextReport) {
                        val of = if (total > 0) " of ${total shr 20} MB" else ""
                        logger.lifecycle("Stockfish:   ${done shr 20} MB$of")
                        nextReport += 10L shl 20
                    }
                }
            }
        }
        connection.disconnect()
    }

    /** Stockfish net names are `nn-<first 12 hex digits of the SHA-256>.nnue`. */
    private fun isValid(file: File, name: String): Boolean {
        if (!file.isFile || file.length() < 1024) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        return name.removePrefix("nn-").startsWith(hex.substring(0, 12))
    }
}
