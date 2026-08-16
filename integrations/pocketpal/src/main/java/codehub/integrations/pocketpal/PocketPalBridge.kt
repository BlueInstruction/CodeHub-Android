package codehub.integrations.pocketpal

import codehub.ai.gateway.AiProviderClient
import codehub.ai.gateway.AiProviderConfig
import codehub.ai.gateway.ChatChunk
import codehub.ai.gateway.ChatRequest
import codehub.ai.models.ModelDescriptor
import codehub.ai.models.ModelRegistry
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface PocketPalBridge {
    suspend fun available(): Boolean
    suspend fun listModels(): List<ModelDescriptor>
    fun client(config: AiProviderConfig): AiProviderClient
}

@Singleton
class DefaultPocketPalBridge @Inject constructor() : PocketPalBridge {

    override suspend fun available(): Boolean = false

    override suspend fun listModels(): List<ModelDescriptor> =
        ModelRegistry.builtinModels.filter { it.provider == "pocketpal" }

    override fun client(config: AiProviderConfig): AiProviderClient = PocketPalClient(config)
}

class PocketPalClient(
    override val config: AiProviderConfig
) : AiProviderClient {

    override suspend fun chat(request: ChatRequest): Flow<ChatChunk> = flow {
        emit(ChatChunk.Error("PocketPal runtime not linked yet"))
    }

    override suspend fun close() {}
}
