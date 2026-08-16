package codehub.terminal.api

import codehub.core.services.AbstractManagedService
import codehub.core.diagnostics.DiagnosticSink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultTerminalService @Inject constructor(
    private val diagnostics: DiagnosticSink,
    private val providers: Map<TerminalBackend, @JvmSuppressWildcards TerminalBackendProvider>
) : TerminalService {

    private val sessionMap = ConcurrentHashMap<String, TerminalSession>()
    private val sessionFlow = MutableStateFlow<List<TerminalSession>>(emptyList())
    private val outputFlow = MutableSharedFlow<TerminalOutput>(extraBufferCapacity = 512)

    override val sessions: Flow<List<TerminalSession>> = sessionFlow.asStateFlow()

    override suspend fun open(backend: TerminalBackend, cwd: String, shell: String?): TerminalSession {
        val provider = providers[backend]
            ?: throw IllegalStateException("No provider for terminal backend $backend")
        if (!provider.isAvailable()) {
            throw IllegalStateException("Terminal backend $backend is not available")
        }
        val session = provider.openSession(cwd, shell, columns = 80, rows = 24)
        sessionMap[session.id] = session
        sessionFlow.value = sessionMap.values.toList().sortedBy { it.startedAt }
        return session
    }

    override suspend fun write(sessionId: String, data: String) {
        val session = sessionMap[sessionId] ?: return
        providers[session.backend]?.write(sessionId, data)
    }

    override suspend fun resize(sessionId: String, columns: Int, rows: Int) {
        val session = sessionMap[sessionId] ?: return
        providers[session.backend]?.resize(sessionId, columns, rows)
    }

    override suspend fun close(sessionId: String) {
        val session = sessionMap.remove(sessionId) ?: return
        providers[session.backend]?.close(sessionId)
        sessionFlow.value = sessionMap.values.toList().sortedBy { it.startedAt }
    }

    override fun output(sessionId: String): Flow<TerminalOutput> = outputFlow.asSharedFlow()
}
