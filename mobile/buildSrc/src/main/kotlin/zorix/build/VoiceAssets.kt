package zorix.build

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.logging.Logger
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject

/**
 * The coach's offline Persian voice: the sherpa-onnx speech engine (Apache-2.0) and the Piper
 * "fa_IR amir" voice (MIT, trained on a CC0 dataset), int8-quantised. Everything is downloaded
 * once from the sherpa-onnx GitHub releases, checked against pinned SHA-256 sums, cached in
 * [cacheDir] and packed into the app, so the finished app speaks without the internet.
 */
object VoiceAssets {
    const val SHERPA_VERSION = "1.13.8"
    private const val RELEASES = "https://github.com/k2-fsa/sherpa-onnx/releases/download"

    val ANDROID_LIBS = Download(
        "sherpa-onnx-v$SHERPA_VERSION-android.tar.bz2",
        "$RELEASES/v$SHERPA_VERSION/sherpa-onnx-v$SHERPA_VERSION-android.tar.bz2",
        "2ff63469a71cb6009aa2e3ed5f4a670f8abdcbe4bb9ffd23776afc792a6b4f44",
    )
    val IOS_FRAMEWORK = Download(
        "sherpa-onnx-v$SHERPA_VERSION-ios-shared-onnxruntime-static.xcframework.zip",
        "$RELEASES/xcframework/sherpa-onnx-v$SHERPA_VERSION-ios-shared-onnxruntime-static.xcframework.zip",
        "e259a7d3b38ad7dec49bb078252a30bb42ede8355e2bb130cf8c1c78ed131f75",
    )
    val PERSIAN_VOICE = Download(
        "vits-piper-fa_IR-amir-medium-int8.tar.bz2",
        "$RELEASES/tts-models/vits-piper-fa_IR-amir-medium-int8.tar.bz2",
        "b79dbf0b6b9629a36fd541bfda83817ec12e2697557d87b918638445971eac46",
    )

    /** Android ABIs the engine is packed for (x86_64 emulators simply have no Persian voice). */
    val ANDROID_ABIS = listOf("arm64-v8a", "armeabi-v7a")

    /** Only the JNI bridge and ONNX Runtime are needed on Android. */
    val ANDROID_SO = listOf("libsherpa-onnx-jni.so", "libonnxruntime.so")

    /** The parts of espeak-ng-data the Persian voice needs (2 MB instead of 19 MB). */
    val ESPEAK_KEEP = listOf("phondata", "phonindex", "phontab", "intonations", "fa_dict", "en_dict", "lang/**", "voices/**")

    /** Changes whenever the packed voice changes, so the app refreshes its unpacked copy. */
    const val VOICE_ID = "fa-amir-medium-int8-1"

    class Download(val name: String, val url: String, val sha256: String)

    /** Returns the cached archive, downloading it first when it is missing or damaged. */
    fun fetch(download: Download, cacheDir: File, logger: Logger): File {
        cacheDir.mkdirs()
        val file = File(cacheDir, download.name)
        if (file.isFile && sha256(file) == download.sha256) return file
        val part = File(cacheDir, download.name + ".part")
        logger.lifecycle("Voice: downloading ${download.name} (one-time)...")
        try {
            httpGet(download.url, part, logger)
        } catch (e: Exception) {
            part.delete()
            throw GradleException("Voice: could not download ${download.url}: ${e.message}", e)
        }
        val sum = sha256(part)
        if (sum != download.sha256) {
            part.delete()
            throw GradleException("Voice: checksum mismatch for ${download.name} (got $sum, expected ${download.sha256})")
        }
        file.delete()
        if (!part.renameTo(file)) {
            part.copyTo(file, overwrite = true)
            part.delete()
        }
        return file
    }

    /** Unpacks the Persian voice into `<assetsRoot>/voice/fa`: model.onnx, tokens.txt and espeak-ng-data. */
    fun unpackVoice(archive: File, assetsRoot: File, archives: ArchiveOperations, fs: FileSystemOperations) {
        val target = File(assetsRoot, "voice/fa")
        target.deleteRecursively()
        val root = "vits-piper-fa_IR-amir-medium-int8"
        fs.copy {
            from(archives.tarTree(archives.bzip2(archive)))
            include("$root/fa_IR-amir-medium.onnx", "$root/tokens.txt", "$root/MODEL_CARD")
            ESPEAK_KEEP.forEach { include("$root/espeak-ng-data/$it") }
            eachFile {
                val rel = relativePath.segments.drop(1).toMutableList()
                if (rel.firstOrNull() == "fa_IR-amir-medium.onnx") rel[0] = "model.onnx"
                path = rel.joinToString("/")
            }
            includeEmptyDirs = false
            into(target)
        }
        if (!File(target, "model.onnx").isFile || !File(target, "espeak-ng-data/fa_dict").isFile) {
            throw GradleException("Voice: unexpected layout in ${archive.name}")
        }
        File(target, "version").writeText(VOICE_ID)
    }

    private fun httpGet(url: String, dest: File, logger: Logger) {
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
                        logger.lifecycle("Voice:   ${done shr 20} MB$of")
                        nextReport += 10L shl 20
                    }
                }
            }
        }
        connection.disconnect()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/**
 * Android: the sherpa-onnx native libraries (as jniLibs) and the Persian voice (as assets).
 * When the download fails and [required] is false the app is built without the Persian voice
 * (the coach then shows its explanations as text only).
 */
abstract class ProvideAndroidVoiceTask : DefaultTask() {
    @get:Input
    abstract val abis: ListProperty<String>

    @get:Input
    abstract val required: Property<Boolean>

    @get:Internal
    abstract val cacheDir: DirectoryProperty

    @get:OutputDirectory
    abstract val jniLibsDir: DirectoryProperty

    @get:OutputDirectory
    abstract val assetsDir: DirectoryProperty

    @get:Inject
    abstract val archives: ArchiveOperations

    @get:Inject
    abstract val fs: FileSystemOperations

    @TaskAction
    fun provide() {
        val libs = jniLibsDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        val assets = assetsDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        try {
            val cache = cacheDir.get().asFile
            val libArchive = VoiceAssets.fetch(VoiceAssets.ANDROID_LIBS, cache, logger)
            val wanted = abis.get()
            fs.copy {
                from(archives.tarTree(archives.bzip2(libArchive)))
                wanted.forEach { abi -> VoiceAssets.ANDROID_SO.forEach { include("jniLibs/$abi/$it") } }
                eachFile { path = relativePath.segments.drop(1).joinToString("/") }
                includeEmptyDirs = false
                into(libs)
            }
            for (abi in wanted) for (so in VoiceAssets.ANDROID_SO) {
                if (!File(libs, "$abi/$so").isFile) throw GradleException("Voice: $so for $abi missing in ${libArchive.name}")
            }
            VoiceAssets.unpackVoice(VoiceAssets.fetch(VoiceAssets.PERSIAN_VOICE, cache, logger), assets, archives, fs)
            logger.lifecycle("Voice: Persian voice and speech engine ready for ${wanted.joinToString()}")
        } catch (e: Exception) {
            if (required.get()) throw e
            libs.deleteRecursively(); libs.mkdirs()
            assets.deleteRecursively(); assets.mkdirs()
            logger.warn("Voice: building WITHOUT the Persian voice (${e.message}). Set -Pzorix.voice.required=true to make this an error.")
        }
    }
}

/**
 * iOS: the sherpa-onnx framework (ONNX Runtime linked in) and the Persian voice files, which the
 * Xcode build copies into the app bundle.
 */
abstract class ProvideIosVoiceTask : DefaultTask() {
    @get:Internal
    abstract val cacheDir: DirectoryProperty

    /** Receives `SherpaOnnxC.xcframework` and `voice/fa`. */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val archives: ArchiveOperations

    @get:Inject
    abstract val fs: FileSystemOperations

    @TaskAction
    fun provide() {
        val out = outputDir.get().asFile
        val cache = cacheDir.get().asFile
        File(out, "SherpaOnnxC.xcframework").deleteRecursively()
        fs.copy {
            from(archives.zipTree(VoiceAssets.fetch(VoiceAssets.IOS_FRAMEWORK, cache, logger)))
            into(out)
        }
        val binary = File(out, "SherpaOnnxC.xcframework/ios-arm64/SherpaOnnxC.framework/SherpaOnnxC")
        if (!binary.isFile) throw GradleException("Voice: unexpected layout in ${VoiceAssets.IOS_FRAMEWORK.name}")
        VoiceAssets.unpackVoice(VoiceAssets.fetch(VoiceAssets.PERSIAN_VOICE, cache, logger), out, archives, fs)
        logger.lifecycle("Voice: sherpa-onnx framework and Persian voice ready for iOS")
    }
}
