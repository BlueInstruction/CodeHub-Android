package io.github.blueinstruction.codehub.ai.gateway

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class OfflineProviderClient(
    override val config: AiProviderConfig
) : AiProviderClient {

    override suspend fun chat(request: ChatRequest): Flow<ChatChunk> = flow {
        val prompt = request.messages.lastOrNull { it.role == "user" }?.content.orEmpty()
        if (prompt.contains("ping", ignoreCase = true)) {
            emit(ChatChunk.Delta("pong"))
        } else if (prompt.contains("list files", ignoreCase = true)) {
            emit(ChatChunk.ToolCallChunk(
                ToolCall(id = "call-1", name = "list_directory", arguments = "{\"path\":\".\"}")
            ))
        } else {
            emit(ChatChunk.Delta("[offline] echo: "))
            prompt.toCharArray().forEachIndexed { i, ch ->
                emit(ChatChunk.Delta(ch.toString()))
                delay(5)
            }
        }
        emit(ChatChunk.Usage(promptTokens = prompt.length / 4, completionTokens = 8))
    }

    override suspend fun close() {}
}
