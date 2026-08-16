package codehub.build.native

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
class ClangBuildProvider @Inject constructor(
    private val processRunner: ProcessRunner
) : BuildToolProvider {

    override val tool: BuildTool = BuildTool.Clang

    override suspend fun isAvailable(): Boolean {
        val result = processRunner.run(
            ProcessSpec(
                command = listOf("which", "clang"),
                workingDirectory = "/data/data/com.termux/files/home",
                environment = emptyMap(),
                timeoutMs = 1_500
            )
        )
        return result.exitCode == 0
    }

    override suspend fun execute(target: BuildTarget): BuildResult {
        val started = System.currentTimeMillis()
        val output = File(target.workspacePath, "a.out").absolutePath
        val sources = target.tasks.filter { it.endsWith(".c") || it.endsWith(".cpp") || it.endsWith(".cc") || it.endsWith(".cxx") }
        if (sources.isEmpty()) {
            return BuildResult(
                target = target,
                status = BuildStatus.Failed,
                exitCode = -1,
                stdout = "",
                stderr = "No source files specified",
                durationMs = 0,
                artifacts = emptyList(),
                diagnostics = emptyList(),
                startedAt = started,
                finishedAt = System.currentTimeMillis()
            )
        }
        val result = processRunner.run(
            ProcessSpec(
                command = listOf("clang++", "-std=c++20", "-O2", "-pthread") + sources + listOf("-o", output),
                workingDirectory = target.workspacePath,
                environment = target.environmentOverrides,
                timeoutMs = 10L * 60 * 1000
            )
        )
        val artifacts = if (result.exitCode == 0) listOf(BuildArtifact(output, File(output).length())) else emptyList()
        return BuildResult(
            target = target,
            status = if (result.exitCode == 0) BuildStatus.Succeeded else BuildStatus.Failed,
            exitCode = result.exitCode,
            stdout = result.stdout,
            stderr = result.stderr,
            durationMs = result.durationMs,
            artifacts = artifacts,
            diagnostics = parseDiagnostics(result.stdout, result.stderr),
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
                tool = "clang"
            )
        }.toList()
    }
}
