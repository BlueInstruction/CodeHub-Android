package codehub.terminal.termux.pty

import codehub.core.diagnostics.DiagnosticEvent
import codehub.core.diagnostics.DiagnosticEventKind
import codehub.core.diagnostics.DiagnosticSeverity
import codehub.core.diagnostics.DiagnosticSink
import codehub.core.diagnostics.DiagnosticStatus
import codehub.terminal.api.TerminalBackend
import codehub.terminal.api.TerminalBackendProvider
import codehub.terminal.api.TerminalOutput
import codehub.terminal.api.TerminalSession
import com.termux.terminal.JNI
import com.termux.terminal.TerminalSession as TermuxTerminalSession
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@Singleton
class PtyBackendProvider @Inject constructor(
    private val diagnostics: DiagnosticSink,
    private val clientFactory: PtyTerminalSessionClientFactory
) : TerminalBackendProvider {

    override val backend: TerminalBackend = TerminalBackend.Termux

    private val sessions = ConcurrentHashMap<String, PtySessionRecord>()

    override suspend fun isAvailable(): Boolean {
        val bashPath = "/data/data/com.termux/files/usr/bin/bash"
        val shPath = "/system/bin/sh"
        return File(bashPath).exists() || File(shPath).exists()
    }

    override suspend fun openSession(
        cwd: String,
        shell: String?,
        columns: Int,
        rows: Int
    ): TerminalSession {
        val shellPath = PtyEnvironment.resolveShell(shell)
        val sessionId = UUID.randomUUID().toString()
        val output = MutableSharedFlow<TerminalOutput>(extraBufferCapacity = 512)
        val env = PtyEnvironment.build(cwd)

        var pid = -1
        val onExit: (Int) -> Unit = { exitCode ->
            sessions[sessionId]?.let { record ->
                diagnostics.emit(
                    DiagnosticEvent.now(
                        kind = DiagnosticEventKind.RuntimeInitialization,
                        severity = DiagnosticSeverity.Info,
                        status = DiagnosticStatus.Ok,
                        source = "PtyBackendProvider",
                        message = "PTY session $sessionId exited with code $exitCode",
                        attributes = mapOf("pid" to pid.toString(), "exit" to exitCode.toString())
                    )
                )
            }
        }
        val onTitle: (String) -> Unit = { title ->
            sessions[sessionId]?.let { record ->
                record.title = title
            }
        }

        val client = clientFactory.create(
            sessionId = sessionId,
            outputSink = output,
            onExit = onExit,
            onTitleChanged = onTitle
        )

        val termuxSession = TermuxTerminalSession(
            shellPath,
            cwd,
            emptyArray(),
            env,
            DEFAULT_TRANSCRIPT_ROWS,
            client
        )
        pid = termuxSession.pid

        val record = PtySessionRecord(
            session = termuxSession,
            output = output,
            client = client,
            shell = shellPath,
            cwd = cwd,
            startedAt = System.currentTimeMillis(),
            columns = columns,
            rows = rows
        )
        sessions[sessionId] = record

        termuxSession.updateSize(columns, rows, DEFAULT_CELL_WIDTH_PX, DEFAULT_CELL_HEIGHT_PX)

        diagnostics.emit(
            DiagnosticEvent.now(
                kind = DiagnosticEventKind.RuntimeInitialization,
                severity = DiagnosticSeverity.Info,
                status = DiagnosticStatus.Ok,
                source = "PtyBackendProvider",
                message = "PTY session $sessionId opened (pid=$pid, shell=$shellPath)",
                attributes = mapOf("cwd" to cwd)
            )
        )

        return TerminalSession(
            id = sessionId,
            backend = backend,
            cwd = cwd,
            shell = shellPath,
            startedAt = System.currentTimeMillis(),
            columns = columns,
            rows = rows
        )
    }

    override suspend fun write(sessionId: String, data: String) {
        val record = sessions[sessionId] ?: return
        record.session.write(data.toByteArray(), 0, data.length)
    }

    override suspend fun resize(sessionId: String, columns: Int, rows: Int) {
        val record = sessions[sessionId] ?: return
        record.columns = columns
        record.rows = rows
        record.session.updateSize(columns, rows, DEFAULT_CELL_WIDTH_PX, DEFAULT_CELL_HEIGHT_PX)
    }

    override suspend fun close(sessionId: String) {
        val record = sessions.remove(sessionId) ?: return
        record.session.finishIfRunning()
        diagnostics.emit(
            DiagnosticEvent.now(
                kind = DiagnosticEventKind.RuntimeInitialization,
                severity = DiagnosticSeverity.Info,
                status = DiagnosticStatus.Ok,
                source = "PtyBackendProvider",
                message = "PTY session $sessionId closed"
            )
        )
    }

    override fun output(sessionId: String): Flow<TerminalOutput> =
        sessions[sessionId]?.output?.asSharedFlow() ?: MutableSharedFlow()

    private data class PtySessionRecord(
        val session: TermuxTerminalSession,
        val output: MutableSharedFlow<TerminalOutput>,
        val client: com.termux.terminal.TerminalSessionClient,
        val shell: String,
        val cwd: String,
        val startedAt: Long,
        var columns: Int,
        var rows: Int,
        var title: String? = null
    )

    companion object {
        private const val DEFAULT_TRANSCRIPT_ROWS = 200
        private const val DEFAULT_CELL_WIDTH_PX = 10
        private const val DEFAULT_CELL_HEIGHT_PX = 20
    }
}
