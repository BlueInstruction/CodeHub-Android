package io.github.blueinstruction.codehub.ai.permissions

import kotlinx.serialization.Serializable

@Serializable
data class AuditEntry(
    val id: String,
    val timestamp: Long,
    val agentSessionId: String,
    val toolName: String,
    val arguments: Map<String, String>,
    val granted: Boolean,
    val decidedBy: String,
    val reason: String?
)

interface AgentAuditLog {
    fun record(entry: AuditEntry)
    fun recent(limit: Int = 100): List<AuditEntry>
}
