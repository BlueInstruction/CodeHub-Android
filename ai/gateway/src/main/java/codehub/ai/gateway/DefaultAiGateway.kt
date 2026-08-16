package codehub.ai.gateway

import codehub.core.diagnostics.DiagnosticEvent
import codehub.core.diagnostics.DiagnosticEventKind
import codehub.core.diagnostics.DiagnosticSeverity
import codehub.core.diagnostics.DiagnosticSink
import codehub.core.diagnostics.DiagnosticStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAiGateway @Inject constructor(
    private val diagnostics: DiagnosticSink
) : AiGateway {

    private val providers = ConcurrentHashMap<String, AiProviderClient>()
    private val providerConfigs = MutableStateFlow<List<AiProviderConfig>>(emptyList())
    private val sessionHistory = ConcurrentHashMap<String, MutableList<ChatMessage>>()

    override val activeProviders = providerConfigs.asStateFlow()

    override suspend fun registerProvider(config: AiProviderConfig) {
        val client = createClient(config)
        providers[config.id] = client
        providerConfigs.value = providerConfigs.value.filter { it.id != config.id } + config
        diagnostics.emit(
            DiagnosticEvent.now(
                kind = DiagnosticEventKind.RuntimeInitialization,
                severity = DiagnosticSeverity.Info,
                status = DiagnosticStatus.Ok,
                source = "AiGateway",
                message = "Provider ${config.displayName} (${config.kind}) registered",
                attributes = mapOf("provider" to config.id)
            )
        )
    }

    override suspend fun removeProvider(id: String) {
        providers.remove(id)?.close()
        providerConfigs.value = providerConfigs.value.filter { it.id != id }
    }

    override suspend fun chat(request: ChatRequest): Flow<ChatChunk> = flow {
        val client = providers[request.providerId]
        if (client == null) {
            emit(ChatChunk.Error("No provider registered for ${request.providerId}"))
            return@flow
        }
        sessionHistory.getOrPut(request.sessionId) { mutableListOf() }
            .addAll(request.messages)
        client.chat(request).collect { chunk ->
            if (chunk is ChatChunk.Delta) {
                sessionHistory[request.sessionId]?.apply {
                    val last = lastOrNull()
                    if (last != null && last.role == "assistant") {
                        this[this.size - 1] = last.copy(content = last.content + chunk.text)
                    } else {
                        add(ChatMessage(role = "assistant", content = chunk.text))
                    }
                }
            }
            emit(chunk)
        }
        emit(ChatChunk.Done(request.sessionId))
    }
        .onEach { chunk ->
            if (chunk is ChatChunk.Error) {
                diagnostics.emit(
                    DiagnosticEvent.now(
                        kind = DiagnosticEventKind.AiBackendFailure,
                        severity = DiagnosticSeverity.Error,
                        status = DiagnosticStatus.Failed,
                        source = "AiGateway",
                        message = chunk.message,
                        attributes = mapOf("session" to request.sessionId, "provider" to request.providerId)
                    )
                )
            }
        }
        .flowOn(Dispatchers.IO)

    override fun sessionHistory(sessionId: String): Flow<List<ChatMessage>> = flow {
        emit(sessionHistory[sessionId]?.toList() ?: emptyList())
    }

    override suspend fun cancel(sessionId: String) {
        sessionHistory.remove(sessionId)
    }

    private suspend fun createClient(config: AiProviderConfig): AiProviderClient {
        return when (config.kind) {
            AiProviderKind.Offline -> OfflineProviderClient(config)
            AiProviderKind.Online -> HttpProviderClient(config)
            AiProviderKind.Custom -> HttpProviderClient(config)
        }
    }
}
