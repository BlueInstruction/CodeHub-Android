package codehub.core.permissions

import kotlinx.coroutines.flow.Flow

interface PermissionDecider {
    fun evaluate(request: ToolRequest): PermissionDecision
    fun pendingApprovals(): Flow<List<ToolRequest>>
    suspend fun submitApproval(request: ToolRequest): PermissionDecision
    suspend fun resolve(requestId: String, granted: Boolean, decidedBy: String): PermissionDecision?
    fun setAgentPolicy(policy: AgentPolicy)
    fun getAgentPolicy(agentId: String): AgentPolicy?
}
