package io.github.blueinstruction.codehub.terminal.local

import io.github.blueinstruction.codehub.core.diagnostics.DiagnosticSink
import io.github.blueinstruction.codehub.core.process.ProcessRunner
import io.github.blueinstruction.codehub.core.process.ProcessSpec
import io.github.blueinstruction.codehub.core.process.RunningProcess
import io.github.blueinstruction.codehub.terminal.api.TerminalBackend
import io.github.blueinstruction.codehub.terminal.api.TerminalBackendProvider
import io.github.blueinstruction.codehub.terminal.api.TerminalOutput
import io.github.blueinstruction.codehub.terminal.api.TerminalSession
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

@Singleton
class LocalRuntimeBackendProvider @Inject constructor(
    private val processRunner: ProcessRunner,
    private val diagnostics: DiagnosticSink
) : TerminalBackendProvider {

    override val backend: TerminalBackend = TerminalBackend.LocalRuntime

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessions = ConcurrentHashMap<String, LocalSessionRecord>()

    override suspend fun isAvailable(): Boolean = true

    override suspend fun openSession(cwd: String, shell: String?, columns: Int, rows: Int): TerminalSession {
        val shellCmd = shell ?: System.getenv("SHELL") ?: "/system/bin/sh"
        val spec = ProcessSpec(
            command = listOf(shellCmd),
            workingDirectory = cwd,
            environment = System.getenv().toMap(),
            timeoutMs = null
        )
        val process = processRunner.launch(spec)
        val sessionId = UUID.randomUUID().toString()
        val output = MutableSharedFlow<TerminalOutput>(extraBufferCapacity = 256)
        val session = TerminalSession(
            id = sessionId,
            backend = backend,
            cwd = cwd,
            shell = shellCmd,
            startedAt = System.currentTimeMillis(),
            columns = columns,
            rows = rows
        )
        val stdoutJob = scope.launch { process.stdout.collect { output.tryEmit(TerminalOutput.Stdout(it, sessionId)) } }
        val stderrJob = scope.launch { process.stderr.collect { output.tryEmit(TerminalOutput.Stderr(it, sessionId)) } }
        val awaitJob = scope.launch {
            val result = process.await()
            output.tryEmit(TerminalOutput.Exit(sessionId, result.exitCode))
        }
        sessions[sessionId] = LocalSessionRecord(process, output, stdoutJob, stderrJob, awaitJob, sessionId)
        return session
    }

    override suspend fun write(sessionId: String, data: String) {
        sessions[sessionId]?.process?.writeStdin(data)
    }

    override suspend fun resize(sessionId: String, columns: Int, rows: Int) {
        sessions[sessionId]
    }

    override suspend fun close(sessionId: String) {
        val rec = sessions.remove(sessionId) ?: return
        rec.stdoutJob.cancel()
        rec.stderrJob.cancel()
        rec.awaitJob.cancel()
        rec.process.kill()
    }

    override fun output(sessionId: String): Flow<TerminalOutput> =
        sessions[sessionId]?.output?.asSharedFlow() ?: MutableSharedFlow()

    private data class LocalSessionRecord(
        val process: RunningProcess,
        val output: MutableSharedFlow<TerminalOutput>,
        val stdoutJob: Job,
        val stderrJob: Job,
        val awaitJob: Job,
        val sessionId: String
    )
}
