package codehub.build.gradle

import codehub.build.api.BuildArtifact
import codehub.build.api.BuildDiagnostic
import codehub.build.api.BuildResult
import codehub.build.api.BuildStatus
import codehub.build.api.BuildTool
import codehub.build.api.BuildToolProvider
import codehub.build.api.BuildTarget
import codehub.core.process.ProcessRunner
import codehub.core.process.ProcessSpec
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GradleBuildProvider @Inject constructor(
    private val processRunner: ProcessRunner
) : BuildToolProvider {

    override val tool: BuildTool = BuildTool.Gradle

    private val wrapperScript = "./gradlew"

    override suspend fun isAvailable(): Boolean {
        return File("/data/data/com.termux/files/usr/bin/gradle").exists() ||
            File(System.getProperty("user.dir"), "gradlew").exists() ||
            File(System.getProperty("user.home"), ".gradle").exists()
    }

    override suspend fun execute(target: BuildTarget): BuildResult {
        val started = System.currentTimeMillis()
        val workspacePath = target.workspacePath
        val isAgpProject = AgpTaskSemantics.isAgpProject(workspacePath)
        val isWrapper = File(workspacePath, "gradlew").exists()
        val gradleBin = if (isWrapper) wrapperScript else "gradle"

        val command = buildList {
            add(gradleBin)
            addAll(target.tasks.map { normalizeTaskName(it, isAgpProject) })
            add("--parallel")
            add("--console=plain")
        }

        val spec = ProcessSpec(
            command = command,
            workingDirectory = workspacePath,
            environment = target.environmentOverrides + mapOf(
                "JAVA_OPTS" to "-Xmx2g",
                "GRADLE_OPTS" to "-Dorg.gradle.daemon=false -Dorg.gradle.parallel=true"
            ),
            timeoutMs = 30L * 60 * 1000,
            redirectStderrToStdout = false
        )
        val result = processRunner.run(spec)
        val diagnostics = if (isAgpProject) {
            AgpDiagnosticParser.parse(result.stdout, result.stderr)
        } else {
            parseDiagnostics(result.stdout, result.stderr)
        }
        val artifacts = if (isAgpProject) {
            locateAgpArtifacts(workspacePath, target.tasks)
        } else {
            findGenericArtifacts(workspacePath)
        }
        val status = if (result.exitCode == 0) BuildStatus.Succeeded else BuildStatus.Failed

        return BuildResult(
            target = target,
            status = status,
            exitCode = result.exitCode,
            stdout = result.stdout,
            stderr = result.stderr,
            durationMs = result.durationMs,
            artifacts = artifacts,
            diagnostics = diagnostics,
            startedAt = started,
            finishedAt = System.currentTimeMillis()
        )
    }

    override fun parseDiagnostics(stdout: String, stderr: String): List<BuildDiagnostic> {
        val combined = stdout + "\n" + stderr
        val pattern = Regex(
            """(?<file>[^\s:]+):(?<line>\d+):(?:(?<col>\d+):)?\s+(?:error:\s*(?<errm>.+)|warning:\s*(?<warnm>.+)|e:\s+(?<em>.+)|w:\s+(?<wm>.+))"""
        )
        return pattern.findAll(combined).map { m ->
            val groups = m.groups
            val severity = when {
                groups["errm"] != null || groups["em"] != null -> "error"
                groups["warnm"] != null || groups["wm"] != null -> "warning"
                else -> "info"
            }
            BuildDiagnostic(
                severity = severity,
                file = groups["file"]?.value,
                line = groups["line"]?.value?.toIntOrNull(),
                column = groups["col"]?.value?.toIntOrNull(),
                code = null,
                message = (groups["errm"]?.value ?: groups["warnm"]?.value ?: groups["em"]?.value ?: groups["wm"]?.value ?: "").trim(),
                tool = "gradle"
            )
        }.toList()
    }

    private fun normalizeTaskName(task: String, isAgpProject: Boolean): String {
        if (!isAgpProject) return task
        val known = AgpTaskSemantics.describe(task)
        return if (known.kind == AgpTaskKind.GenericGradle) {
            if (task.startsWith(":")) task else ":app:$task"
        } else {
            ":app:${known.taskName}"
        }
    }

    private fun locateAgpArtifacts(workspacePath: String, tasks: List<String>): List<BuildArtifact> {
        val descriptors = AgpTaskSemantics.describeAll(tasks)
        val hasBundleTask = descriptors.any { it.kind == AgpTaskKind.BundleDebug || it.kind == AgpTaskKind.BundleRelease }
        val hasLintTask = descriptors.any { it.kind == AgpTaskKind.LintDebug || it.kind == AgpTaskKind.LintRelease }
        val hasAssembleOrInstallTask = descriptors.any {
            it.kind == AgpTaskKind.AssembleDebug ||
            it.kind == AgpTaskKind.AssembleRelease ||
            it.kind == AgpTaskKind.InstallDebug ||
            it.kind == AgpTaskKind.InstallRelease
        }

        val artifacts = mutableListOf<BuildArtifact>()
        if (hasAssembleOrInstallTask) artifacts += AndroidArtifactLocator.findApks(workspacePath)
        if (hasBundleTask) artifacts += AndroidArtifactLocator.findAabs(workspacePath)
        if (hasLintTask) artifacts += AndroidArtifactLocator.findLintReports(workspacePath)
        if (artifacts.isEmpty()) artifacts += AndroidArtifactLocator.findAll(workspacePath)
        return artifacts.distinctBy { it.path }
    }

    private fun findGenericArtifacts(workspacePath: String): List<BuildArtifact> {
        val outputs = listOf(
            "build/outputs/apk",
            "build/outputs/aar",
            "build/outputs/jar",
            "build/libs",
            "app/build/outputs/apk"
        )
        return outputs.flatMap { dir ->
            val f = File(workspacePath, dir)
            if (!f.exists()) emptyList()
            else f.walkTopDown().filter { it.isFile }.map {
                BuildArtifact(path = it.absolutePath, sizeBytes = it.length())
            }.toList()
        }.distinctBy { it.path }
    }
}
