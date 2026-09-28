package zorix.build

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

/**
 * Compiles Stockfish into one executable per [StockfishTarget] and lays them out as
 * `outputDir/<abi>/<fileName>`, ready to be registered as a jniLibs source directory.
 *
 * Every translation unit is compiled in parallel through the Gradle worker API,
 * then each executable is linked with full LTO, exactly like Stockfish's own Makefile does.
 */
@CacheableTask
abstract class BuildStockfishTask @Inject constructor(
    private val workers: WorkerExecutor,
) : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDir: DirectoryProperty

    /** Absolute path of the clang++ driver to use. */
    @get:Input
    abstract val compiler: Property<String>

    /** Optional --sysroot (the NDK sysroot). */
    @get:Input
    @get:Optional
    abstract val sysroot: Property<String>

    @get:Input
    abstract val targets: ListProperty<StockfishTarget>

    @get:Input
    abstract val compilerFlags: ListProperty<String>

    @get:Input
    abstract val linkerFlags: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:LocalState
    abstract val objectDir: DirectoryProperty

    @TaskAction
    fun build() {
        val src = sourceDir.get().asFile
        val clang = File(compiler.get())
        if (!clang.isFile) throw GradleException("Stockfish: compiler not found: $clang")
        val sources = StockfishTargets.sourcesFromMakefile(src)
        if (sources.isEmpty()) throw GradleException("Stockfish: no sources found in $src")

        val out = outputDir.get().asFile
        out.deleteRecursively()
        val objRoot = objectDir.get().asFile
        objRoot.deleteRecursively()

        val sysrootFlags = sysroot.orNull?.let { listOf("--sysroot=$it") }.orEmpty()
        val queue = workers.noIsolation()
        val linkJobs = mutableListOf<Pair<StockfishTarget, List<File>>>()

        logger.lifecycle("Stockfish: compiling ${sources.size} files for ${targets.get().joinToString { it.id }}")
        for (target in targets.get()) {
            val objDir = File(objRoot, target.id).apply { mkdirs() }
            val flags = listOf("--target=${target.triple}") + sysrootFlags + compilerFlags.get() +
                target.flags + "-DARCH=${target.arch}"
            val objects = sources.map { rel ->
                val obj = File(objDir, rel.replace('/', '_').removeSuffix(".cpp") + ".o")
                queue.submit(RunTool::class.java) {
                    command.set(listOf(clang.absolutePath) + flags + listOf("-c", File(src, rel).absolutePath, "-o", obj.absolutePath))
                    workingDir.set(src.absolutePath)
                    label.set("Compiling $rel for ${target.id}")
                }
                obj
            }
            linkJobs += target to objects
        }
        queue.await()

        for ((target, objects) in linkJobs) {
            val exe = File(out, "${target.abi}/${target.fileName}").apply { parentFile.mkdirs() }
            // LTO needs the optimisation flags again at link time.
            val flags = listOf("--target=${target.triple}") + sysrootFlags + compilerFlags.get() + target.flags + linkerFlags.get()
            queue.submit(RunTool::class.java) {
                command.set(listOf(clang.absolutePath) + flags + objects.map { it.absolutePath } + listOf("-o", exe.absolutePath))
                workingDir.set(src.absolutePath)
                label.set("Linking ${target.abi}/${target.fileName}")
            }
        }
        queue.await()

        for ((target, _) in linkJobs) {
            val exe = File(out, "${target.abi}/${target.fileName}")
            if (!exe.isFile) throw GradleException("Stockfish: ${exe.name} was not produced")
            exe.setExecutable(true, false)
            logger.lifecycle("Stockfish: built ${target.abi}/${target.fileName} (${exe.length() / 1024} KB)")
        }
    }
}

/** Runs one compiler / linker command inside a Gradle worker. */
abstract class RunTool : WorkAction<RunTool.Params> {

    interface Params : WorkParameters {
        val command: ListProperty<String>
        val workingDir: Property<String>
        val label: Property<String>
    }

    override fun execute() {
        val cmd = parameters.command.get()
        val process = ProcessBuilder(cmd)
            .directory(File(parameters.workingDir.get()))
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val code = process.waitFor()
        if (code != 0) {
            throw GradleException(
                "Stockfish: ${parameters.label.get()} failed (exit code $code)\n$output\nCommand: ${cmd.joinToString(" ")}",
            )
        }
    }
}
