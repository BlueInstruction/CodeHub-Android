package codehub.build.ninja

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
class NinjaBuildProvider @Inject constructor(
    private val processRunner: ProcessRunner
) : BuildToolProvider {

    override val tool: BuildTool = BuildTool.Ninja

    override suspend fun isAvailable(): Boolean {
        val result = processRunner.run(
            ProcessSpec(
                command = listOf("which", "ninja"),
                workingDirectory = "/data/data/com.termux/files/home",
                environment = emptyMap(),
                timeoutMs = 1_500
            )
        )
        return result.exitCode == 0
    }

    override suspend fun execute(target: BuildTarget): BuildResult {
        val started = System.currentTimeMillis()
        val ninjaFile = File(target.workspacePath, "build.ninja")
        if (!ninjaFile.exists()) {
            return BuildResult(
                target = target,
                status = BuildStatus.Failed,
                exitCode = -1,
                stdout = "",
                stderr = "build.ninja not found in ${target.workspacePath}",
                durationMs = System.currentTimeMillis() - started,
                artifacts = emptyList(),
                diagnostics = emptyList(),
                startedAt = started,
                finishedAt = System.currentTimeMillis()
            )
        }
        val tasks = if (target.tasks.isEmpty()) listOf("all") else target.tasks
        val result = processRunner.run(
            ProcessSpec(
                command = listOf("ninja", "-C", target.workspacePath, "-j", target.parallelJobs.toString()) + tasks,
                workingDirectory = target.workspacePath,
                environment = target.environmentOverrides,
                timeoutMs = 30L * 60 * 1000
            )
        )
        return BuildResult(
            target = target,
            status = if (result.exitCode == 0) BuildStatus.Succeeded else BuildStatus.Failed,
            exitCode = result.exitCode,
            stdout = result.stdout,
            stderr = result.stderr,
            durationMs = result.durationMs,
            artifacts = findArtifacts(target.workspacePath),
            diagnostics = parseDiagnostics(result.stdout, result.stderr),
            startedAt = started,
            finishedAt = System.currentTimeMillis()
        )
    }

    override fun parseDiagnostics(stdout: String, stderr: String): List<BuildDiagnostic> {
        val combined = stdout + "\n" + stderr
        val pattern = Regex(
            """(?<file>[^\s:]+):(?<line>\d+):(?<col>\d+):\s+(?<severity>error|warning|note|fatal):\s*(?<msg>.+)"""
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
                tool = "ninja"
            )
        }.toList()
    }

    private fun findArtifacts(workspacePath: String): List<BuildArtifact> {
        val root = File(workspacePath)
        return root.walkTopDown()
            .filter { it.isFile && it.extension in setOf("so", "a", "out", "bin", "exe") }
            .map { BuildArtifact(it.absolutePath, it.length()) }
            .toList()
    }
}
