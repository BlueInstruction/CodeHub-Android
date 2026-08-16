package codehub.terminal.termux

import android.content.Context
import codehub.core.diagnostics.DiagnosticEvent
import codehub.core.diagnostics.DiagnosticEventKind
import codehub.core.diagnostics.DiagnosticSeverity
import codehub.core.diagnostics.DiagnosticSink
import codehub.core.diagnostics.DiagnosticStatus
import codehub.core.process.ProcessRunner
import codehub.core.process.ProcessSpec
import codehub.core.process.RunningProcess
import codehub.terminal.api.TerminalBackend
import codehub.terminal.api.TerminalBackendProvider
import codehub.terminal.api.TerminalOutput
import codehub.terminal.api.TerminalSession
import java.io.File
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
class TermuxBackendProvider @Inject constructor(
    private val processRunner: ProcessRunner,
    private val diagnostics: DiagnosticSink,
    private val context: Context
) : TerminalBackendProvider {

    override val backend: TerminalBackend = TerminalBackend.Termux

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessions = ConcurrentHashMap<String, TermuxSessionRecord>()

    override suspend fun isAvailable(): Boolean {
        val pkg = runCatching { context.packageManager.getApplicationInfo("com.termux", 0) }.getOrNull()
        if (pkg != null) return true
        val termuxPath = "/data/data/com.termux/files/usr/bin/login"
        return File(termuxPath).exists()
    }

    override suspend fun openSession(cwd: String, shell: String?, columns: Int, rows: Int): TerminalSession {
        val shellCmd = shell ?: "/data/data/com.termux/files/usr/bin/bash"
        val spec = ProcessSpec(
            command = listOf("/system/bin/sh", "-c", "exec $shellCmd -l"),
            workingDirectory = cwd,
            environment = mapOf(
                "HOME" to "/data/data/com.termux/files/home",
                "PREFIX" to "/data/data/com.termux/files/usr",
                "TERM" to "xterm-256color"
            ),
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
        val stdoutJob = scope.launch {
            process.stdout.collect { output.tryEmit(TerminalOutput.Stdout(it, sessionId)) }
        }
        val stderrJob = scope.launch {
            process.stderr.collect { output.tryEmit(TerminalOutput.Stderr(it, sessionId)) }
        }
        val awaitJob = scope.launch {
            val result = process.await()
            output.tryEmit(TerminalOutput.Exit(sessionId, result.exitCode))
        }
        sessions[sessionId] = TermuxSessionRecord(
            process = process,
            output = output,
            stdoutJob = stdoutJob,
            stderrJob = stderrJob,
            awaitJob = awaitJob,
            sessionId = sessionId
        )
        diagnostics.emit(
            DiagnosticEvent.now(
                kind = DiagnosticEventKind.RuntimeInitialization,
                severity = DiagnosticSeverity.Info,
                status = DiagnosticStatus.Ok,
                source = "TermuxBackend",
                message = "Termux session $sessionId opened"
            )
        )
        return session
    }

    override suspend fun write(sessionId: String, data: String) {
        val rec = sessions[sessionId] ?: return
        rec.process.writeStdin(data)
    }

    override suspend fun resize(sessionId: String, columns: Int, rows: Int) {
        sessions[sessionId] ?: return
    }

    override suspend fun close(sessionId: String) {
        val rec = sessions.remove(sessionId) ?: return
        rec.stdoutJob.cancel()
        rec.stderrJob.cancel()
        rec.awaitJob.cancel()
        rec.process.kill()
        diagnostics.emit(
            DiagnosticEvent.now(
                kind = DiagnosticEventKind.RuntimeInitialization,
                severity = DiagnosticSeverity.Info,
                status = DiagnosticStatus.Ok,
                source = "TermuxBackend",
                message = "Termux session $sessionId closed"
            )
        )
    }

    override fun output(sessionId: String): Flow<TerminalOutput> =
        sessions[sessionId]?.output?.asSharedFlow() ?: MutableSharedFlow()

    private data class TermuxSessionRecord(
        val process: RunningProcess,
        val output: MutableSharedFlow<TerminalOutput>,
        val stdoutJob: Job,
        val stderrJob: Job,
        val awaitJob: Job,
        val sessionId: String
    )
}
