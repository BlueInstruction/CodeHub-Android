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
class DefaultPermissionDecider @Inject constructor(
    private val diagnostics: DiagnosticSink
) : PermissionDecider {

    private val policies = ConcurrentHashMap<String, AgentPolicy>()
    private val pending = MutableStateFlow<List<ToolRequest>>(emptyList())
    private val decisions = MutableSharedFlow<PermissionDecision>(extraBufferCapacity = 64)
    private val pendingById = ConcurrentHashMap<String, ToolRequest>()

    override fun evaluate(request: ToolRequest): PermissionDecision {
        val policy = policies[request.agentSessionId]
        val allowed = policy != null &&
            request.toolName in policy.allowedTools &&
            request.toolName !in policy.deniedTools &&
            PermissionPolicy.levelAllows(policy.defaultLevel, request.toolName)

        if (!allowed || (PermissionPolicy.isDestructive(request.toolName) && policy?.requireApprovalForDestructive == true)) {
            pendingById[request.requestId()] = request
            pending.value = pending.value + request
            val event = DiagnosticEvent.now(
                kind = DiagnosticEventKind.AgentApprovalRequired,
                severity = DiagnosticSeverity.Warn,
                status = DiagnosticStatus.Pending,
                source = "PermissionDecider",
                message = "Approval required for ${request.toolName}",
                reason = "destructive=${PermissionPolicy.isDestructive(request.toolName)}",
                attributes = mapOf(
                    "agent" to request.agentSessionId,
                    "workspace" to request.workspaceId
                )
            )
            diagnostics.emit(event)
            return PermissionDecision(
                request = request,
                granted = false,
                grantLevel = null,
                reason = "Pending user approval",
                decidedBy = "system"
            )
        }

        val decision = PermissionDecision(
            request = request,
            granted = true,
            grantLevel = policy?.defaultLevel,
            reason = "Allowed by policy",
            decidedBy = "system"
        )
        diagnostics.emit(
            DiagnosticEvent.now(
                kind = DiagnosticEventKind.AgentToolBlocked,
                severity = DiagnosticSeverity.Info,
                status = DiagnosticStatus.Ok,
                source = "PermissionDecider",
                message = "Tool ${request.toolName} granted to ${request.agentSessionId}"
            )
        )
        return decision
    }

    override fun pendingApprovals(): Flow<List<ToolRequest>> = pending.asStateFlow()

    override suspend fun submitApproval(request: ToolRequest): PermissionDecision {
        return evaluate(request)
    }

    override suspend fun resolve(requestId: String, granted: Boolean, decidedBy: String): PermissionDecision? {
        val request = pendingById.remove(requestId) ?: return null
        pending.value = pending.value.filter { it.requestId() != requestId }
        val decision = PermissionDecision(
            request = request,
            granted = granted,
            grantLevel = if (granted) request.requestedLevel else null,
            reason = if (granted) "Approved by $decidedBy" else "Denied by $decidedBy",
            decidedBy = decidedBy
        )
        decisions.tryEmit(decision)
        diagnostics.emit(
            DiagnosticEvent.now(
                kind = DiagnosticEventKind.AgentToolBlocked,
                severity = if (granted) DiagnosticSeverity.Info else DiagnosticSeverity.Warn,
                status = if (granted) DiagnosticStatus.Ok else DiagnosticStatus.Blocked,
                source = "PermissionDecider",
                message = "Tool ${request.toolName} ${if (granted) "approved" else "denied"} by $decidedBy"
            )
        )
        return decision
    }

    override fun setAgentPolicy(policy: AgentPolicy) {
        policies[policy.agentId] = policy
    }

    override fun getAgentPolicy(agentId: String): AgentPolicy? = policies[agentId]

    val decisionStream: Flow<PermissionDecision> get() = decisions.asSharedFlow()

    private fun ToolRequest.requestId(): String =
        arguments["__requestId"] ?: UUID.randomUUID().toString()
}
