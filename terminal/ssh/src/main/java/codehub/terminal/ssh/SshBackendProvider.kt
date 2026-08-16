package codehub.terminal.ssh

import codehub.core.diagnostics.DiagnosticSink
import codehub.core.process.ProcessRunner
import codehub.core.process.ProcessSpec
import codehub.terminal.api.TerminalBackend
import codehub.terminal.api.TerminalBackendProvider
import codehub.terminal.api.TerminalOutput
import codehub.terminal.api.TerminalSession
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

@Singleton
class SshBackendProvider @Inject constructor(
    private val processRunner: ProcessRunner,
    private val diagnostics: DiagnosticSink
) : TerminalBackendProvider {

    override val backend: TerminalBackend = TerminalBackend.Ssh

    private val sessions = ConcurrentHashMap<String, TerminalSession>()
    private val targets = ConcurrentHashMap<String, SshTarget>()

    override suspend fun isAvailable(): Boolean {
        val result = processRunner.run(
            ProcessSpec(
                command = listOf("which", "ssh"),
                workingDirectory = "/data/data/com.termux/files/home",
                environment = emptyMap(),
                timeoutMs = 1_500
            )
        )
        return result.exitCode == 0
    }

    override suspend fun openSession(cwd: String, shell: String?, columns: Int, rows: Int): TerminalSession {
        val id = UUID.randomUUID().toString()
        val session = TerminalSession(
            id = id,
            backend = backend,
            cwd = cwd,
            shell = shell ?: "ssh",
            startedAt = System.currentTimeMillis(),
            columns = columns,
            rows = rows
        )
        sessions[id] = session
        return session
    }

    fun attachTarget(sessionId: String, target: SshTarget) {
        targets[sessionId] = target
    }

    override suspend fun write(sessionId: String, data: String) {
        val target = targets[sessionId] ?: return
        val cmd = if (target.password != null) {
            listOf("sshpass", "-p", target.password, "ssh", "-p", target.port.toString(), "${target.user}@${target.host}")
        } else {
            listOf("ssh", "-p", target.port.toString(), "${target.user}@${target.host}")
        }
        processRunner.run(
            ProcessSpec(
                command = cmd + listOf("-T", data),
                workingDirectory = "/data/data/com.termux/files/home",
                environment = emptyMap(),
                timeoutMs = 30_000
            )
        )
    }

    override suspend fun resize(sessionId: String, columns: Int, rows: Int) {
        sessions[sessionId]
    }

    override suspend fun close(sessionId: String) {
        sessions.remove(sessionId)
        targets.remove(sessionId)
    }

    override fun output(sessionId: String): Flow<TerminalOutput> = MutableSharedFlow()
}
