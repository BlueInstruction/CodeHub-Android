package codehub.core.diagnostics

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
enum class DiagnosticEventKind {
    RuntimeInitialization,
    BuildStarted,
    BuildCompleted,
    BuildFailed,
    ProcessLaunch,
    ProcessExit,
    ProcessCrash,
    MissingDependency,
    PermissionDenied,
    TerminalFailure,
    VulkanInitialization,
    AiBackendFailure,
    ServiceStarted,
    ServiceStopped,
    ServiceFailed,
    AgentApprovalRequired,
    AgentToolBlocked,
    UserMessage
}

@Serializable
enum class DiagnosticSeverity { Info, Warn, Error, Fatal }

@Serializable
enum class DiagnosticStatus { Ok, Skipped, Failed, Blocked, Pending }

@Serializable
data class DiagnosticEvent(
    val id: String,
    val timestamp: Long,
    val kind: DiagnosticEventKind,
    val severity: DiagnosticSeverity,
    val status: DiagnosticStatus,
    val source: String,
    val message: String,
    val reason: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val relatedEventIds: List<String> = emptyList()
) {
    companion object {
        fun now(
            kind: DiagnosticEventKind,
            severity: DiagnosticSeverity,
            status: DiagnosticStatus,
            source: String,
            message: String,
            reason: String? = null,
            attributes: Map<String, String> = emptyMap(),
            relatedEventIds: List<String> = emptyList()
        ): DiagnosticEvent {
            val id = "${kind.name.lowercase()}-${System.currentTimeMillis()}-${counter.incrementAndGet()}"
            return DiagnosticEvent(
                id = id,
                timestamp = System.currentTimeMillis(),
                kind = kind,
                severity = severity,
                status = status,
                source = source,
                message = message,
                reason = reason,
                attributes = attributes,
                relatedEventIds = relatedEventIds
            )
        }

        private val counter = java.util.concurrent.atomic.AtomicLong(0)
    }
}

interface DiagnosticSink {
    fun emit(event: DiagnosticEvent)
    fun events(): Flow<DiagnosticEvent>
    fun snapshot(): List<DiagnosticEvent>
}
