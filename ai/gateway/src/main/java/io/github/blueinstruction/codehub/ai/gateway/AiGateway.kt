package io.github.blueinstruction.codehub.ai.gateway

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
enum class AiProviderKind { Offline, Online, Custom }

@Serializable
data class AiProviderConfig(
    val id: String,
    val displayName: String,
    val kind: AiProviderKind,
    val endpoint: String?,
    val apiKey: String?,
    val defaultModel: String?,
    val supportsStreaming: Boolean,
    val supportsToolCalls: Boolean
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
    val name: String? = null
)

@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String
)

@Serializable
data class ChatRequest(
    val providerId: String,
    val model: String?,
    val messages: List<ChatMessage>,
    val temperature: Float = 0.2f,
    val maxTokens: Int? = null,
    val tools: List<String> = emptyList(),
    val sessionId: String,
    val stream: Boolean = true
)

@Serializable
sealed interface ChatChunk {
    @Serializable data class Delta(val text: String) : ChatChunk
    @Serializable data class ToolCallChunk(val toolCall: ToolCall) : ChatChunk
    @Serializable data class Usage(val promptTokens: Int, val completionTokens: Int) : ChatChunk
    @Serializable data class Done(val sessionId: String) : ChatChunk
    @Serializable data class Error(val message: String) : ChatChunk
}

interface AiGateway {
    val activeProviders: Flow<List<AiProviderConfig>>
    suspend fun registerProvider(config: AiProviderConfig)
    suspend fun removeProvider(id: String)
    suspend fun chat(request: ChatRequest): Flow<ChatChunk>
    fun sessionHistory(sessionId: String): Flow<List<ChatMessage>>
    suspend fun cancel(sessionId: String)
}

interface AiProviderClient {
    val config: AiProviderConfig
    suspend fun chat(request: ChatRequest): Flow<ChatChunk>
    suspend fun close()
}
