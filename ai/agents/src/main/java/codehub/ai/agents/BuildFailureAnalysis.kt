package codehub.ai.agents

import codehub.ai.gateway.AiGateway
import codehub.ai.gateway.ChatMessage
import codehub.ai.gateway.ChatRequest
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
    private val diagnostics: DiagnosticSink
) {

    private val resultFlow = MutableSharedFlow<AnalysisResult>(extraBufferCapacity = 16)

    val results: Flow<AnalysisResult> = resultFlow.asSharedFlow()

    suspend fun analyze(
        workspacePath: String,
        providerId: String,
        failingContext: String,
        failureType: FailureType,
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

        val logcatSnapshot = runCatching {
            logcatService.snapshot(filter = null, limit = 200)
        }.getOrDefault(emptyList())

        val systemPrompt = buildSystemPrompt(failureType)
        val userPrompt = buildUserPrompt(workspacePath, failingContext, logcatSnapshot, failureType)

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
            rootCauseHypothesis = collectedResponse.toString(),
            suggestedPatch = extractPatchBlock(collectedResponse.toString()),
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
            appendLine("You are a mobile engineering workstation assistant analyzing a $type failure.")
            appendLine("Respond in this exact format:")
            appendLine("ROOT_CAUSE:")
            appendLine("<one-paragraph hypothesis>")
            appendLine("EVIDENCE:")
            appendLine("<bullet list of supporting evidence from the provided context>")
            appendLine("PATCH:")
            appendLine("```diff")
            appendLine("--- <path>")
            appendLine("+++ <path>")
            appendLine("@@ <hunk header> @@")
            appendLine(" <context line>")
            appendLine("-<removed line>")
            appendLine("+<added line>")
            appendLine("```")
            appendLine("If no patch is appropriate, write PATCH: NONE")
        }
    }

    private fun buildUserPrompt(
        workspacePath: String,
        failingContext: String,
        logcatSnapshot: List<codehub.devtools.logcat.LogcatEntry>,
        type: FailureType
    ): String {
        return buildString {
            appendLine("Workspace: $workspacePath")
            appendLine("Failure type: $type")
            appendLine()
            appendLine("=== Failure context (stderr / build log) ===")
            appendLine(failingContext.take(8000))
            appendLine()
            if (logcatSnapshot.isNotEmpty()) {
                appendLine("=== Recent Logcat (${logcatSnapshot.size} entries) ===")
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
}

enum class FailureType { BuildConfigure, BuildCompile, ApkInstall, AppLaunch, LogcatCrash }

data class AnalysisResult(
    val sessionId: String,
    val workspacePath: String,
    val failureType: FailureType,
    val rootCauseHypothesis: String,
    val suggestedPatch: String?,
    val logcatEntries: List<codehub.devtools.logcat.LogcatEntry>,
    val errors: String?
)
