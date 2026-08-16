package codehub.core.diagnostics

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class InMemoryDiagnosticSinkTest {

    @Test
    fun `emit and observe receives event`() = runTest {
        val sink = InMemoryDiagnosticSink()
        sink.events().test {
            val event = DiagnosticEvent.now(
                kind = DiagnosticEventKind.BuildStarted,
                severity = DiagnosticSeverity.Info,
                status = DiagnosticStatus.Ok,
                source = "BuildService",
                message = "Build started"
            )
            sink.emit(event)
            val received = awaitItem()
            assertThat(received.id).isEqualTo(event.id)
            assertThat(received.kind).isEqualTo(DiagnosticEventKind.BuildStarted)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `snapshot returns emitted events in order`() = runTest {
        val sink = InMemoryDiagnosticSink()
        repeat(5) { i ->
            sink.emit(
                DiagnosticEvent.now(
                    kind = DiagnosticEventKind.UserMessage,
                    severity = DiagnosticSeverity.Info,
                    status = DiagnosticStatus.Ok,
                    source = "test",
                    message = "m$i"
                )
            )
        }
        val snapshot = sink.snapshot()
        assertThat(snapshot).hasSize(5)
        assertThat(snapshot.map { it.message }).containsExactly("m0", "m1", "m2", "m3", "m4").inOrder()
    }

    @Test
    fun `ring buffer evicts oldest entries when exceeding capacity`() = runTest {
        val sink = InMemoryDiagnosticSink()
        repeat(3000) { i ->
            sink.emit(
                DiagnosticEvent.now(
                    kind = DiagnosticEventKind.UserMessage,
                    severity = DiagnosticSeverity.Info,
                    status = DiagnosticStatus.Ok,
                    source = "test",
                    message = "m$i"
                )
            )
        }
        val snapshot = sink.snapshot()
        assertThat(snapshot.size).isAtMost(2048)
    }
}
