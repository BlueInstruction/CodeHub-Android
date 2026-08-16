package codehub.terminal.api

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
enum class TerminalBackend { Termux, LocalRuntime, Ssh }

@Serializable
data class TerminalSession(
    val id: String,
    val backend: TerminalBackend,
    val cwd: String,
    val shell: String,
    val startedAt: Long,
    val columns: Int = 80,
    val rows: Int = 24
)

@Serializable
sealed interface TerminalOutput {
    @Serializable data class Stdout(val data: String, val sessionId: String) : TerminalOutput
    @Serializable data class Stderr(val data: String, val sessionId: String) : TerminalOutput
    @Serializable data class Exit(val sessionId: String, val code: Int) : TerminalOutput
}

interface TerminalService {
    val sessions: Flow<List<TerminalSession>>
    suspend fun open(backend: TerminalBackend, cwd: String, shell: String?): TerminalSession
    suspend fun write(sessionId: String, data: String)
    suspend fun resize(sessionId: String, columns: Int, rows: Int)
    suspend fun close(sessionId: String)
    fun output(sessionId: String): Flow<TerminalOutput>
}

interface TerminalBackendProvider {
    val backend: TerminalBackend
    suspend fun isAvailable(): Boolean
    suspend fun openSession(cwd: String, shell: String?, columns: Int, rows: Int): TerminalSession
    suspend fun write(sessionId: String, data: String)
    suspend fun resize(sessionId: String, columns: Int, rows: Int)
    suspend fun close(sessionId: String)
    fun output(sessionId: String): Flow<TerminalOutput>
}
