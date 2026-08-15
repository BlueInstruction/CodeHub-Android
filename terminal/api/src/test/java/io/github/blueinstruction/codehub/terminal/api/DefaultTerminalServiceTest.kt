package io.github.blueinstruction.codehub.terminal.api

import com.google.common.truth.Truth.assertThat
import io.github.blueinstruction.codehub.core.diagnostics.InMemoryDiagnosticSink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultTerminalServiceTest {

    private val sink = InMemoryDiagnosticSink()

    @Test
    fun `open returns session and exposes it via sessions flow`() = runTest {
        val provider = FakeProvider()
        val service = DefaultTerminalService(sink, mapOf(TerminalBackend.Termux to provider))
        val session = service.open(TerminalBackend.Termux, "/tmp", null)
        assertThat(session.backend).isEqualTo(TerminalBackend.Termux)
        assertThat(session.cwd).isEqualTo("/tmp")
        assertThat(provider.opened).isTrue()
    }

    @Test
    fun `close removes session`() = runTest {
        val provider = FakeProvider()
        val service = DefaultTerminalService(sink, mapOf(TerminalBackend.Termux to provider))
        val session = service.open(TerminalBackend.Termux, "/tmp", null)
        service.close(session.id)
        assertThat(provider.closed).isTrue()
    }
}

private class FakeProvider : TerminalBackendProvider {
    var opened = false
    var closed = false

    override val backend: TerminalBackend = TerminalBackend.Termux

    override suspend fun isAvailable(): Boolean = true

    override suspend fun openSession(cwd: String, shell: String?, columns: Int, rows: Int): TerminalSession {
        opened = true
        return TerminalSession(
            id = "fake-${System.currentTimeMillis()}",
            backend = backend,
            cwd = cwd,
            shell = shell ?: "sh",
            startedAt = System.currentTimeMillis(),
            columns = columns,
            rows = rows
        )
    }

    override suspend fun write(sessionId: String, data: String) {}

    override suspend fun resize(sessionId: String, columns: Int, rows: Int) {}

    override suspend fun close(sessionId: String) {
        closed = true
    }

    override fun output(sessionId: String): Flow<TerminalOutput> = MutableSharedFlow<TerminalOutput>().asSharedFlow()
}
