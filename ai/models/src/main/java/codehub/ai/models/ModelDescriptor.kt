package codehub.ai.models

import kotlinx.serialization.Serializable

@Serializable
data class ModelDescriptor(
    val id: String,
    val displayName: String,
    val provider: String,
    val contextWindow: Int,
    val supportsToolCalls: Boolean,
    val supportsStreaming: Boolean,
    val quantization: String? = null,
    val sizeBytes: Long? = null,
    val architecture: String? = null
)

@Serializable
data class LocalModelManifest(
    val rootPath: String,
    val models: List<ModelDescriptor>
)

object ModelRegistry {
    val builtinModels = listOf(
        ModelDescriptor(
            id = "pocketpal-default",
            displayName = "PocketPal Default",
            provider = "pocketpal",
            contextWindow = 4096,
            supportsToolCalls = false,
            supportsStreaming = true,
            quantization = "Q4_K_M",
            sizeBytes = 4_000_000_000L,
            architecture = "gguf"
        ),
        ModelDescriptor(
            id = "llama.cpp-default",
            displayName = "llama.cpp Local",
            provider = "llamacpp",
            contextWindow = 8192,
            supportsToolCalls = false,
            supportsStreaming = true,
            quantization = "Q4_K_M",
            sizeBytes = 4_500_000_000L,
            architecture = "gguf"
        )
    )
}
