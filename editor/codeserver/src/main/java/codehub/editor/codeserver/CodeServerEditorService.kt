package codehub.editor.codeserver

import codehub.core.diagnostics.DiagnosticEvent
import codehub.core.diagnostics.DiagnosticEventKind
import codehub.core.diagnostics.DiagnosticSeverity
import codehub.core.diagnostics.DiagnosticSink
import codehub.core.diagnostics.DiagnosticStatus
import codehub.core.process.ProcessRunner
import codehub.core.process.ProcessSpec
import codehub.core.services.AbstractManagedService
import codehub.editor.api.EditorBackend
import codehub.editor.api.EditorBackendProvider
import codehub.editor.api.EditorEndpoint
import codehub.editor.api.EditorEvent
import codehub.editor.api.EditorService
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class CodeServerEditorService @Inject constructor(
    private val processRunner: ProcessRunner,
    private val diagnostics: DiagnosticSink,
    private val backendProvider: CodeServerBackendProvider
) : EditorService, AbstractManagedService("code-server") {

    private val currentEndpoint = MutableStateFlow<EditorEndpoint?>(null)
    private val eventStream = MutableSharedFlow<EditorEvent>(extraBufferCapacity = 32)

    override val current: Flow<EditorEndpoint?> get() = currentEndpoint.asStateFlow()
    override val events: Flow<EditorEvent> get() = eventStream.asSharedFlow()

    override suspend fun start(backend: EditorBackend): EditorEndpoint {
        if (backend != EditorBackend.CodeServer) {
            throw IllegalArgumentException("CodeServerEditorService only supports CodeServer backend")
        }
        if (!backendProvider.isAvailable()) {
            val event = EditorEvent.Failed(backend, "code-server binary not found")
            eventStream.tryEmit(event)
            diagnostics.emit(
                DiagnosticEvent.now(
                    kind = DiagnosticEventKind.RuntimeInitialization,
                    severity = DiagnosticSeverity.Warn,
                    status = DiagnosticStatus.Skipped,
                    source = "CodeServerEditorService",
                    message = "code-server binary not found",
                    reason = "ui_only_mode"
                )
            )
            currentEndpoint.value = EditorEndpoint(
                backend = EditorBackend.VsCodeWeb,
                url = "https://vscode.dev",
                port = null,
                token = null,
                requiresAuth = false
            )
            eventStream.tryEmit(EditorEvent.Started(currentEndpoint.value!!))
            return currentEndpoint.value!!
        }
        val endpoint = backendProvider.start()
        currentEndpoint.value = endpoint
        eventStream.tryEmit(EditorEvent.Started(endpoint))
        return endpoint
    }

    override suspend fun stop() {
        backendProvider.stop()
        val backend = currentEndpoint.value?.backend ?: return
        currentEndpoint.value = null
        eventStream.tryEmit(EditorEvent.Stopped(backend))
    }

    override suspend fun restart(): EditorEndpoint? {
        stop()
        return start(EditorBackend.CodeServer)
    }

    override suspend fun onStart() {
        start(EditorBackend.CodeServer)
    }

    override suspend fun onStop() {
        stop()
    }
}

interface CodeServerBackendProvider {
    suspend fun isAvailable(): Boolean
    suspend fun start(): EditorEndpoint
    suspend fun stop()
}

@Singleton
class TermuxCodeServerBackendProvider @Inject constructor(
    private val processRunner: ProcessRunner,
    private val diagnostics: DiagnosticSink
) : CodeServerBackendProvider {

    @Volatile private var currentPort: Int = 0
    @Volatile private var currentToken: String? = null

    override suspend fun isAvailable(): Boolean {
        val result = processRunner.run(
            ProcessSpec(
                command = listOf("which", "code-server"),
                workingDirectory = "/data/data/com.termux/files/home",
                environment = emptyMap(),
                timeoutMs = 2_000
            )
        )
        return result.exitCode == 0 && result.stdout.isNotBlank()
    }

    override suspend fun start(): EditorEndpoint {
        currentPort = findFreePort()
        currentToken = generateToken()
        val home = File("/data/data/com.termux/files/home")
        val spec = ProcessSpec(
            command = listOf(
                "code-server",
                "--bind-addr", "127.0.0.1:$currentPort",
                "--auth", "password",
                "--disable-telemetry",
                "--disable-update-check"
            ),
            workingDirectory = home.absolutePath,
            environment = mapOf(
                "PASSWORD" to (currentToken ?: "")
            ),
            timeoutMs = null
        )
        val process = processRunner.launch(spec)
        diagnostics.emit(
            DiagnosticEvent.now(
                kind = DiagnosticEventKind.RuntimeInitialization,
                severity = DiagnosticSeverity.Info,
                status = DiagnosticStatus.Ok,
                source = "CodeServerBackend",
                message = "code-server launched on 127.0.0.1:$currentPort (pid=${process.pid})"
            )
        )
        return EditorEndpoint(
            backend = EditorBackend.CodeServer,
            url = "http://127.0.0.1:$currentPort",
            port = currentPort,
            token = currentToken,
            requiresAuth = true
        )
    }

    override suspend fun stop() {
        if (currentPort == 0) return
        processRunner.run(
            ProcessSpec(
                command = listOf("pkill", "-f", "code-server.*--bind-addr 127.0.0.1:$currentPort"),
                workingDirectory = "/data/data/com.termux/files/home",
                environment = emptyMap(),
                timeoutMs = 2_000
            )
        )
        currentPort = 0
        currentToken = null
    }

    private fun findFreePort(): Int {
        val socket = java.net.ServerSocket(0)
        return socket.use { it.localPort }
    }

    private fun generateToken(): String {
        val bytes = ByteArray(24)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { String.format("%02x", it) }
    }
}
