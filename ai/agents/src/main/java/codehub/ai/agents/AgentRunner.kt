package codehub.ai.agents

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
enum class AgentState { Idle, Thinking, CallingTool, WaitingApproval, Responding, Stopped, Failed }

@Serializable
data class AgentSession(
    val id: String,
    val workspaceId: String,
    val providerId: String,
    val systemPrompt: String,
    val state: AgentState,
    val createdAt: Long,
    val updatedAt: Long,
    val toolCallsMade: Int = 0,
    val tokensUsed: Int = 0
)

@Serializable
sealed interface AgentEvent {
    @Serializable data class StateChanged(val sessionId: String, val state: AgentState) : AgentEvent
    @Serializable data class ToolCallRequested(val sessionId: String, val toolName: String, val arguments: Map<String, String>) : AgentEvent
    @Serializable data class ToolCallResult(val sessionId: String, val toolName: String, val ok: Boolean, val output: String) : AgentEvent
    @Serializable data class Message(val sessionId: String, val role: String, val content: String) : AgentEvent
    @Serializable data class ApprovalRequired(val sessionId: String, val requestId: String, val toolName: String, val reason: String) : AgentEvent
    @Serializable data class Failed(val sessionId: String, val message: String) : AgentEvent
}

interface AgentRunner {
    val sessions: Flow<List<AgentSession>>
    val events: Flow<AgentEvent>
    suspend fun createSession(workspaceId: String, providerId: String, systemPrompt: String): AgentSession
    suspend fun send(sessionId: String, userMessage: String)
    suspend fun resolveApproval(sessionId: String, requestId: String, granted: Boolean)
    suspend fun stop(sessionId: String)
    suspend fun get(sessionId: String): AgentSession?
}
