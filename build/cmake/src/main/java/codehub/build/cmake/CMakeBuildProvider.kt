package codehub.build.cmake

import codehub.build.api.BuildArtifact
import codehub.build.api.BuildDiagnostic
import codehub.build.api.BuildResult
import codehub.build.api.BuildStatus
import codehub.build.api.BuildTarget
import codehub.build.api.BuildTool
import codehub.build.api.BuildToolProvider
import codehub.core.process.ProcessRunner
import codehub.core.process.ProcessSpec
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CMakeBuildProvider @Inject constructor(
    private val processRunner: ProcessRunner
) : BuildToolProvider {

    override val tool: BuildTool = BuildTool.CMake

    override suspend fun isAvailable(): Boolean {
        val result = processRunner.run(
            ProcessSpec(
                command = listOf("which", "cmake"),
                workingDirectory = "/data/data/com.termux/files/home",
                environment = emptyMap(),
                timeoutMs = 1_500
            )
        )
        return result.exitCode == 0
    }

    override suspend fun execute(target: BuildTarget): BuildResult {
        val started = System.currentTimeMillis()
        val buildDir = File(target.workspacePath, "build")
        buildDir.mkdirs()
        val configureCmd = listOf(
            "cmake",
            "-S", target.workspacePath,
            "-B", buildDir.absolutePath,
            "-DCMAKE_BUILD_TYPE=Release",
            "-G", "Ninja"
        ) + target.tasks.filter { it.startsWith("-D") }
        val configureResult = processRunner.run(
            ProcessSpec(
                command = configureCmd,
                workingDirectory = target.workspacePath,
                environment = target.environmentOverrides,
                timeoutMs = 10L * 60 * 1000
            )
        )
        if (configureResult.exitCode != 0) {
            return BuildResult(
                target = target,
                status = BuildStatus.Failed,
                exitCode = configureResult.exitCode,
                stdout = configureResult.stdout,
                stderr = configureResult.stderr,
                durationMs = System.currentTimeMillis() - started,
                artifacts = emptyList(),
                diagnostics = parseDiagnostics(configureResult.stdout, configureResult.stderr),
                startedAt = started,
                finishedAt = System.currentTimeMillis()
            )
        }
        val buildResult = processRunner.run(
            ProcessSpec(
                command = listOf("cmake", "--build", buildDir.absolutePath, "--parallel", target.parallelJobs.toString()),
                workingDirectory = target.workspacePath,
                environment = target.environmentOverrides,
                timeoutMs = 30L * 60 * 1000
            )
        )
        val diagnostics = parseDiagnostics(buildResult.stdout, buildResult.stderr)
        val artifacts = buildDir.walkTopDown().filter { it.isFile && it.extension in setOf("so", "a", "out") }
            .map { BuildArtifact(it.absolutePath, it.length()) }
            .toList()
        return BuildResult(
            target = target,
            status = if (buildResult.exitCode == 0) BuildStatus.Succeeded else BuildStatus.Failed,
            exitCode = buildResult.exitCode,
            stdout = configureResult.stdout + "\n" + buildResult.stdout,
            stderr = configureResult.stderr + "\n" + buildResult.stderr,
            durationMs = System.currentTimeMillis() - started,
            artifacts = artifacts,
            diagnostics = diagnostics,
            startedAt = started,
            finishedAt = System.currentTimeMillis()
        )
    }

    override fun parseDiagnostics(stdout: String, stderr: String): List<BuildDiagnostic> {
        val combined = stdout + "\n" + stderr
        val pattern = Regex(
            """(?<file>[^\s:]+):(?<line>\d+):(?<col>\d+):\s+(?<severity>error|warning|note):\s*(?<msg>.+)"""
        )
        return pattern.findAll(combined).map { m ->
            val groups = m.groups
            BuildDiagnostic(
                severity = groups["severity"]?.value ?: "info",
                file = groups["file"]?.value,
                line = groups["line"]?.value?.toIntOrNull(),
                column = groups["col"]?.value?.toIntOrNull(),
                code = null,
                message = (groups["msg"]?.value ?: "").trim(),
                tool = "cmake"
            )
        }.toList()
    }
}
