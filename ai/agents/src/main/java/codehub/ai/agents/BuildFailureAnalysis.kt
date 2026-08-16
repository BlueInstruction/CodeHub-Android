package codehub.ai.agents

import codehub.ai.gateway.AiGateway
import codehub.ai.gateway.ChatMessage
import codehub.ai.gateway.ChatRequest
import codehub.build.api.BuildDiagnostic
import codehub.build.api.BuildResult
import codehub.build.api.BuildStatus
import codehub.core.diagnostics.DiagnosticEvent
import codehub.core.diagnostics.DiagnosticEventKind
import codehub.core.diagnostics.DiagnosticSeverity
import codehub.core.diagnostics.DiagnosticSink
import codehub.core.diagnostics.DiagnosticStatus
import codehub.devtools.logcat.LogcatService
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@Singleton
class BuildFailureAnalysis @Inject constructor(
    private val gateway: AiGateway,
    private val logcatService: LogcatService,
    private val diagnostics: DiagnosticSink,
    private val contextGatherer: ProjectContextGatherer
) {

    private val resultFlow = MutableSharedFlow<AnalysisResult>(extraBufferCapacity = 16)

    val results: Flow<AnalysisResult> = resultFlow.asSharedFlow()

    suspend fun analyze(
        workspacePath: String,
        providerId: String,
        failingContext: String,
        failureType: FailureType,
        buildResult: BuildResult? = null,
        packageName: String? = null,
        sessionId: String = "failure-${System.currentTimeMillis()}"
    ): AnalysisResult {
        diagnostics.emit(
            DiagnosticEvent.now(
                kind = DiagnosticEventKind.AiBackendFailure,
                severity = DiagnosticSeverity.Info,
                status = DiagnosticStatus.Pending,
                source = "BuildFailureAnalysis",
                message = "Requesting AI failure analysis for $workspacePath",
                attributes = mapOf(
                    "session" to sessionId,
                    "failure_type" to failureType.name
                )
            )
        )

        val buildDiagnostics = buildResult?.diagnostics ?: emptyList()
        val projectContext = if (buildDiagnostics.isNotEmpty()) {
            contextGatherer.gatherFromDiagnostics(workspacePath, buildDiagnostics)
        } else {
            contextGatherer.gather(workspacePath)
        }

        val logcatSnapshot = if (packageName != null) {
            runCatching { logcatService.snapshotForPackage(packageName, filter = null, limit = 100) }
                .getOrDefault(emptyList())
        } else {
            runCatching { logcatService.snapshot(filter = null, limit = 100) }
                .getOrDefault(emptyList())
        }

        val systemPrompt = buildSystemPrompt(failureType)
        val userPrompt = buildUserPrompt(
            workspacePath = workspacePath,
            failingContext = failingContext,
            buildResult = buildResult,
            buildDiagnostics = buildDiagnostics,
            projectContext = projectContext,
            logcatSnapshot = logcatSnapshot,
            failureType = failureType,
            packageName = packageName
        )

        val request = ChatRequest(
            providerId = providerId,
            model = null,
            messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = userPrompt)
            ),
            tools = emptyList(),
            sessionId = sessionId,
            stream = false,
            temperature = 0.1f,
            maxTokens = 2000
        )

        val collectedResponse = StringBuilder()
        val collectedErrors = StringBuilder()
        gateway.chat(request).collect { chunk ->
            when (chunk) {
                is codehub.ai.gateway.ChatChunk.Delta -> collectedResponse.append(chunk.text)
                is codehub.ai.gateway.ChatChunk.Error -> {
                    collectedErrors.append(chunk.message).append('\n')
                    diagnostics.emit(
                        DiagnosticEvent.now(
                            kind = DiagnosticEventKind.AiBackendFailure,
                            severity = DiagnosticSeverity.Warn,
                            status = DiagnosticStatus.Failed,
                            source = "BuildFailureAnalysis",
                            message = "AI provider error: ${chunk.message}",
                            attributes = mapOf("session" to sessionId)
                        )
                    )
                }
                else -> Unit
            }
        }

        val result = AnalysisResult(
            sessionId = sessionId,
            workspacePath = workspacePath,
            failureType = failureType,
            rootCauseHypothesis = extractSection(collectedResponse.toString(), "ROOT_CAUSE"),
            evidence = extractSection(collectedResponse.toString(), "EVIDENCE"),
            suggestedPatch = extractPatchBlock(collectedResponse.toString()),
            fullResponse = collectedResponse.toString(),
            projectContext = projectContext,
            buildDiagnostics = buildDiagnostics,
            logcatEntries = logcatSnapshot,
            errors = collectedErrors.toString().trim().ifBlank { null }
        )
        resultFlow.tryEmit(result)
        diagnostics.emit(
            DiagnosticEvent.now(
                kind = DiagnosticEventKind.AiBackendFailure,
                severity = DiagnosticSeverity.Info,
                status = DiagnosticStatus.Ok,
                source = "BuildFailureAnalysis",
                message = "AI failure analysis completed (${collectedResponse.length} chars)",
                attributes = mapOf("session" to sessionId)
            )
        )
        return result
    }

    private fun buildSystemPrompt(type: FailureType): String {
        return buildString {
            appendLine("You are a mobile engineering workstation assistant analyzing a $type failure in an Android project.")
            appendLine("You will receive the project's Gradle files, manifest, source files referenced by compiler diagnostics, the failing build output, AGP errors, and logcat.")
            appendLine("Respond in this exact format:")
            appendLine("ROOT_CAUSE:")
            appendLine("<one-paragraph hypothesis>")
            appendLine("EVIDENCE:")
            appendLine("<bullet list of supporting evidence from the provided context, citing file:line where possible>")
            appendLine("PATCH:")
            appendLine("```diff")
            appendLine("--- <path>")
            appendLine("+++ <path>")
            appendLine("@@ <hunk header> @@")
            appendLine(" <context line>")
            appendLine("-<removed line>")
            appendLine("+<added line>")
            appendLine("```")
            appendLine("If no patch is appropriate (e.g. configuration issue, missing SDK component), write PATCH: NONE and explain what configuration change is needed.")
        }
    }

    private fun buildUserPrompt(
        workspacePath: String,
        failingContext: String,
        buildResult: BuildResult?,
        buildDiagnostics: List<BuildDiagnostic>,
        projectContext: ProjectContextGatherer.ProjectContext,
        logcatSnapshot: List<codehub.devtools.logcat.LogcatEntry>,
        failureType: FailureType,
        packageName: String?
    ): String {
        return buildString {
            appendLine("Workspace: $workspacePath")
            appendLine("Failure type: $failureType")
            if (packageName != null) appendLine("Package: $packageName")
            appendLine()

            appendLine("=== settings.gradle.kts ===")
            projectContext.settingsGradle?.let { appendLine(it) } ?: appendLine("(not found)")
            appendLine()

            appendLine("=== root build.gradle.kts ===")
            projectContext.buildGradleRoot?.let { appendLine(it) } ?: appendLine("(not found)")
            appendLine()

            appendLine("=== app/build.gradle.kts ===")
            projectContext.appBuildGradle?.let { appendLine(it) } ?: appendLine("(not found)")
            appendLine()

            appendLine("=== gradle/libs.versions.toml ===")
            projectContext.versionCatalog?.let { appendLine(it) } ?: appendLine("(not found)")
            appendLine()

            appendLine("=== app/src/main/AndroidManifest.xml ===")
            projectContext.androidManifest?.let { appendLine(it) } ?: appendLine("(not found)")
            appendLine()

            appendLine("=== gradle.properties ===")
            projectContext.gradleProperties?.let { appendLine(it) } ?: appendLine("(not found)")
            appendLine()

            appendLine("=== gradle/wrapper/gradle-wrapper.properties ===")
            projectContext.gradleWrapperProperties?.let { appendLine(it) } ?: appendLine("(not found)")
            appendLine()

            if (buildResult != null) {
                appendLine("=== Build result ===")
                appendLine("Status: ${buildResult.status}")
                appendLine("Exit code: ${buildResult.exitCode}")
                appendLine("Duration: ${buildResult.durationMs}ms")
                appendLine("Task: ${buildResult.target.tasks.joinToString(" ")}")
                appendLine("Tool: ${buildResult.target.tool}")
                appendLine()
            }

            if (buildDiagnostics.isNotEmpty()) {
                appendLine("=== Compiler / AGP diagnostics (${buildDiagnostics.size} entries) ===")
                buildDiagnostics.take(20).forEach { d ->
                    val loc = listOfNotNull(d.file, d.line?.toString(), d.column?.toString()).joinToString(":")
                    appendLine("[${d.severity}] $loc [${d.tool}] ${d.message}")
                }
                if (buildDiagnostics.size > 20) {
                    appendLine("... and ${buildDiagnostics.size - 20} more")
                }
                appendLine()
            }

            if (projectContext.referencedSourceFiles.isNotEmpty()) {
                appendLine("=== Source files referenced by diagnostics (${projectContext.referencedSourceFiles.size} files) ===")
                projectContext.referencedSourceFiles.forEach { refFile ->
                    appendLine("--- ${refFile.relativePath} ---")
                    appendLine(refFile.content)
                    appendLine()
                }
            }

            appendLine("=== Build output (stdout, last 4000 chars) ===")
            appendLine(buildResult?.stdout?.takeLast(4000) ?: failingContext.takeLast(4000))
            appendLine()

            appendLine("=== Build output (stderr, last 4000 chars) ===")
            appendLine(buildResult?.stderr?.takeLast(4000) ?: "")
            appendLine()

            if (logcatSnapshot.isNotEmpty()) {
                appendLine("=== Recent Logcat (${logcatSnapshot.size} entries for package $packageName) ===")
                logcatSnapshot.take(50).forEach { entry ->
                    appendLine("${entry.timestamp} ${entry.level}/${entry.tag}: ${entry.message}")
                }
            }
        }
    }

    private fun extractPatchBlock(response: String): String? {
        val regex = Regex("```diff\\s*\\n(.*?)\\n```", RegexOption.DOT_MATCHES_ALL)
        val match = regex.find(response) ?: return null
        val block = match.groups[1]?.value?.trim() ?: return null
        if (block.isBlank() || block.startsWith("NONE")) return null
        return block
    }

    private fun extractSection(response: String, sectionName: String): String? {
        val regex = Regex("$sectionName:\\s*\\n(.*?)(?=\\n[A-Z_]+:|```|\$)", RegexOption.DOT_MATCHES_ALL)
        val match = regex.find(response) ?: return null
        val content = match.groups[1]?.value?.trim()
        return if (content.isNullOrBlank()) null else content
    }
}

enum class FailureType { BuildConfigure, BuildCompile, ApkInstall, AppLaunch, LogcatCrash, Sync }

data class AnalysisResult(
    val sessionId: String,
    val workspacePath: String,
    val failureType: FailureType,
    val rootCauseHypothesis: String?,
    val evidence: String?,
    val suggestedPatch: String?,
    val fullResponse: String,
    val projectContext: ProjectContextGatherer.ProjectContext,
    val buildDiagnostics: List<BuildDiagnostic>,
    val logcatEntries: List<codehub.devtools.logcat.LogcatEntry>,
    val errors: String?
) {
    val status: BuildStatus? get() = null
}
