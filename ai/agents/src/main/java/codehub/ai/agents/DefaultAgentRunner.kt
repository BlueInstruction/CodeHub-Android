package codehub.ai.agents

import codehub.ai.gateway.AiGateway
import codehub.ai.gateway.ChatChunk
import codehub.ai.gateway.ChatMessage
import codehub.ai.gateway.ChatRequest
import codehub.ai.gateway.ToolCall
import codehub.ai.tools.AgentTool
import codehub.ai.tools.ToolInvocation
import codehub.ai.context.ContextRetriever
import codehub.core.diagnostics.DiagnosticSink
import codehub.core.permissions.AgentPolicy
import codehub.core.permissions.PermissionDecider
import codehub.core.permissions.PermissionLevel
import codehub.core.permissions.ToolRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAgentRunner @Inject constructor(
    private val gateway: AiGateway,
    private val tools: Map<String, @JvmSuppressWildcards AgentTool>,
    private val permissionDecider: PermissionDecider,
    private val contextRetriever: ContextRetriever,
    private val diagnostics: DiagnosticSink
) : AgentRunner {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionMap = ConcurrentHashMap<String, AgentSession>()
    private val sessionFlow = MutableStateFlow<List<AgentSession>>(emptyList())
    private val eventFlow = MutableSharedFlow<AgentEvent>(extraBufferCapacity = 256)

    override val sessions: Flow<List<AgentSession>> = sessionFlow.asStateFlow()
    override val events: Flow<AgentEvent> = eventFlow.asSharedFlow()

    init {
        permissionDecider.setAgentPolicy(
            AgentPolicy(
                agentId = AGENT_DEFAULT_ID,
                defaultLevel = PermissionLevel.WORKSPACE_WRITE,
                allowedTools = tools.keys,
                deniedTools = emptySet(),
                requireApprovalForDestructive = true,
                workspaceScope = null
            )
        )
    }

    override suspend fun createSession(workspaceId: String, providerId: String, systemPrompt: String): AgentSession {
        val now = System.currentTimeMillis()
        val session = AgentSession(
            id = UUID.randomUUID().toString(),
            workspaceId = workspaceId,
            providerId = providerId,
            systemPrompt = systemPrompt,
            state = AgentState.Idle,
            createdAt = now,
            updatedAt = now
        )
        sessionMap[session.id] = session
        publishSession(session)
        return session
    }

    override suspend fun send(sessionId: String, userMessage: String) {
        val session = sessionMap[sessionId] ?: return
        scope.launch {
            updateState(session.copy(state = AgentState.Thinking))
            val relevant = runCatching {
                contextRetriever.retrieve(session.workspaceId, userMessage).take(8)
            }.getOrDefault(emptyList())
            val systemMessage = ChatMessage(role = "system", content = session.systemPrompt)
            val contextMessage = if (relevant.isEmpty()) null else ChatMessage(
                role = "system",
                content = "Relevant context:\n" + relevant.joinToString("\n---\n") { it }
            )
            val request = ChatRequest(
                providerId = session.providerId,
                model = null,
                messages = listOfNotNull(systemMessage, contextMessage, ChatMessage(role = "user", content = userMessage)),
                tools = tools.keys.toList(),
                sessionId = session.id,
                stream = true
            )
            try {
                gateway.chat(request).collect { chunk ->
                    when (chunk) {
                        is ChatChunk.Delta -> {
                            eventFlow.tryEmit(AgentEvent.Message(sessionId, "assistant", chunk.text))
                        }
                        is ChatChunk.ToolCallChunk -> {
                            handleToolCall(session, chunk.toolCall)
                        }
                        is ChatChunk.Done -> updateState(session.copy(state = AgentState.Idle))
                        is ChatChunk.Error -> {
                            eventFlow.tryEmit(AgentEvent.Failed(sessionId, chunk.message))
                            updateState(session.copy(state = AgentState.Failed))
                        }
                        is ChatChunk.Usage -> {
                            updateState(session.copy(tokensUsed = session.tokensUsed + chunk.completionTokens))
                        }
                    }
                }
            } catch (t: Throwable) {
                eventFlow.tryEmit(AgentEvent.Failed(sessionId, t.stackTraceToString()))
                updateState(session.copy(state = AgentState.Failed))
            }
        }
    }

    private suspend fun handleToolCall(session: AgentSession, toolCall: ToolCall) {
        val tool = tools[toolCall.name]
        if (tool == null) {
            eventFlow.tryEmit(AgentEvent.ToolCallResult(session.id, toolCall.name, ok = false, output = "unknown tool ${toolCall.name}"))
            return
        }
        val arguments = parseArguments(toolCall.arguments)
        val request = ToolRequest(
            toolName = toolCall.name,
            arguments = arguments + ("__requestId" to toolCall.id),
            requestedLevel = if (tool.descriptor.requiresApproval) PermissionLevel.WORKSPACE_WRITE else PermissionLevel.READ_ONLY,
            workspaceId = session.workspaceId,
            agentSessionId = AGENT_DEFAULT_ID
        )
        val decision = permissionDecider.evaluate(request)
        if (!decision.granted) {
            eventFlow.tryEmit(
                AgentEvent.ApprovalRequired(session.id, toolCall.id, toolCall.name, decision.reason ?: "approval required")
            )
            updateState(session.copy(state = AgentState.WaitingApproval))
            return
        }
        eventFlow.tryEmit(AgentEvent.ToolCallRequested(session.id, toolCall.name, arguments))
        updateState(session.copy(state = AgentState.CallingTool, toolCallsMade = session.toolCallsMade + 1))
        val invocation = ToolInvocation(
            id = toolCall.id,
            toolName = toolCall.name,
            arguments = arguments,
            workspaceId = session.workspaceId
        )
        val result = runCatching { tool.invoke(invocation) }.getOrElse {
            codehub.ai.tools.ToolResult(
                invocationId = invocation.id,
                ok = false,
                output = "",
                error = it.message
            )
        }
        eventFlow.tryEmit(AgentEvent.ToolCallResult(session.id, toolCall.name, result.ok, result.output))
        updateState(session.copy(state = AgentState.Idle))
    }

    override suspend fun resolveApproval(sessionId: String, requestId: String, granted: Boolean) {
        permissionDecider.resolve(requestId, granted, decidedBy = "user")
        updateState((sessionMap[sessionId] ?: return).copy(state = AgentState.Idle))
    }

    override suspend fun stop(sessionId: String) {
        updateState((sessionMap[sessionId] ?: return).copy(state = AgentState.Stopped))
    }

    override suspend fun get(sessionId: String): AgentSession? = sessionMap[sessionId]

    private fun parseArguments(json: String): Map<String, String> {
        if (json.isBlank()) return emptyMap()
        return runCatching {
            val obj = kotlinx.serialization.json.Json.parseToJsonElement(json) as kotlinx.serialization.json.JsonObject
            obj.mapValues { (_, v) -> v.toString().trim('"') }
        }.getOrDefault(emptyMap())
    }

    private fun updateState(session: AgentSession) {
        sessionMap[session.id] = session
        publishSession(session)
    }

    private fun publishSession(session: AgentSession) {
        sessionFlow.value = sessionMap.values.toList().sortedBy { it.createdAt }
        eventFlow.tryEmit(AgentEvent.StateChanged(session.id, session.state))
    }

    companion object {
        private const val AGENT_DEFAULT_ID = "codehub-default-agent"
    }
}
