package codehub.core.permissions

import codehub.core.diagnostics.DiagnosticEvent
import codehub.core.diagnostics.DiagnosticEventKind
import codehub.core.diagnostics.DiagnosticSeverity
import codehub.core.diagnostics.DiagnosticSink
import codehub.core.diagnostics.DiagnosticStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DangerousCommandGuard @Inject constructor(
    private val diagnostics: DiagnosticSink
) {
    private val permanentAllowlist = ConcurrentHashMap.newKeySet<String>()
    private val permanentDenylist = ConcurrentHashMap.newKeySet<String>()
    private val decisions = MutableSharedFlow<DangerousDecision>(extraBufferCapacity = 64)
    private val pending = MutableStateFlow<List<DangerousRequest>>(emptyList())
    private val pendingById = ConcurrentHashMap<String, DangerousRequest>()

    val pendingRequests: StateFlow<List<DangerousRequest>> = pending.asStateFlow()
    val decisionStream: Flow<DangerousDecision> = decisions.asSharedFlow()

    fun evaluate(command: String, sessionId: String, workspaceId: String): DangerousDecision {
        val description = BashArity.describe(command)
        if (permanentAllowlist.contains(description)) {
            return DangerousDecision(command = command, allowed = true, reason = "allowlisted", decidedBy = "system")
        }
        if (permanentDenylist.contains(description)) {
            return DangerousDecision(command = command, allowed = false, reason = "denylisted", decidedBy = "system")
        }
        val match = DangerousCommandPatterns.evaluate(command)
        if (match == null) {
            return DangerousDecision(command = command, allowed = true, reason = "no dangerous pattern", decidedBy = "system")
        }
        val requestId = UUID.randomUUID().toString()
        val request = DangerousRequest(
            requestId = requestId,
            command = command,
            description = description,
            match = match,
            sessionId = sessionId,
            workspaceId = workspaceId
        )
        pendingById[requestId] = request
        pending.value = pending.value + request
        diagnostics.emit(
            DiagnosticEvent.now(
                kind = DiagnosticEventKind.AgentApprovalRequired,
                severity = when (match.severity) {
                    DangerousCommandPatterns.Severity.Warn -> DiagnosticSeverity.Warn
                    DangerousCommandPatterns.Severity.Dangerous -> DiagnosticSeverity.Warn
                    DangerousCommandPatterns.Severity.Catastrophic -> DiagnosticSeverity.Error
                },
                status = DiagnosticStatus.Pending,
                source = "DangerousCommandGuard",
                message = "Approval required: ${match.description}",
                reason = match.patternKey,
                attributes = mapOf(
                    "severity" to match.severity.name,
                    "session" to sessionId,
                    "workspace" to workspaceId,
                    "command" to command.take(200)
                )
            )
        )
        return DangerousDecision(
            command = command,
            allowed = false,
            reason = "pending approval: ${match.description}",
            decidedBy = "system"
        )
    }

    suspend fun resolve(requestId: String, allowed: Boolean, scope: DecisionScope, decidedBy: String): DangerousDecision? {
        val request = pendingById.remove(requestId) ?: return null
        pending.value = pending.value.filter { it.requestId != requestId }
        when (scope) {
            DecisionScope.Once -> Unit
            DecisionScope.AlwaysAllow -> permanentAllowlist.add(request.description)
            DecisionScope.AlwaysDeny -> permanentDenylist.add(request.description)
        }
        val decision = DangerousDecision(
            command = request.command,
            allowed = allowed,
            reason = if (allowed) "approved by $decidedBy (${scope.name})" else "denied by $decidedBy (${scope.name})",
            decidedBy = decidedBy
        )
        decisions.tryEmit(decision)
        diagnostics.emit(
            DiagnosticEvent.now(
                kind = DiagnosticEventKind.AgentToolBlocked,
                severity = if (allowed) DiagnosticSeverity.Info else DiagnosticSeverity.Warn,
                status = if (allowed) DiagnosticStatus.Ok else DiagnosticStatus.Blocked,
                source = "DangerousCommandGuard",
                message = "Command ${if (allowed) "approved" else "denied"}: ${request.match.description}",
                reason = request.match.patternKey
            )
        )
        return decision
    }

    fun clearAllowlist() {
        permanentAllowlist.clear()
    }

    fun clearDenylist() {
        permanentDenylist.clear()
    }
}

data class DangerousRequest(
    val requestId: String,
    val command: String,
    val description: String,
    val match: DangerousCommandPatterns.DangerousMatch,
    val sessionId: String,
    val workspaceId: String
)

data class DangerousDecision(
    val command: String,
    val allowed: Boolean,
    val reason: String,
    val decidedBy: String
)

enum class DecisionScope { Once, AlwaysAllow, AlwaysDeny }
