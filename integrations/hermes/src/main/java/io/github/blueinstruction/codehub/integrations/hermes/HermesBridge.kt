package io.github.blueinstruction.codehub.integrations.hermes

import io.github.blueinstruction.codehub.ai.gateway.AiProviderClient
import io.github.blueinstruction.codehub.ai.gateway.AiProviderConfig
import io.github.blueinstruction.codehub.ai.gateway.ChatChunk
import io.github.blueinstruction.codehub.ai.gateway.ChatRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface HermesBridge {
    suspend fun available(): Boolean
    fun client(config: AiProviderConfig): AiProviderClient
}

@Singleton
class DefaultHermesBridge @Inject constructor() : HermesBridge {

    override suspend fun available(): Boolean = false

    override fun client(config: AiProviderConfig): AiProviderClient = object : AiProviderClient {
        override val config = config
        override suspend fun chat(request: ChatRequest): Flow<ChatChunk> = flow {
            emit(ChatChunk.Error("Hermes backend not yet integrated"))
        }
        override suspend fun close() {}
    }
}
