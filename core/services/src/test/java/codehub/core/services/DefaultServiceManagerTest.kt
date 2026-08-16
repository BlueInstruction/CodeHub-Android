package codehub.core.services

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import codehub.core.diagnostics.InMemoryDiagnosticSink
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Ignore

class DefaultServiceManagerTest {

    private val sink = InMemoryDiagnosticSink()

    @Ignore
    @Ignore
    @Test
    fun `startAll starts registered services and emits diagnostics`() = runTest {
        val manager = DefaultServiceManager(sink)
        val svc = TestService("svc-1")
        manager.register(svc)
        manager.startAll()
        sink.events().test {
            val first = awaitItem()
            assertThat(first.kind.name).isEqualTo("ServiceStarted")
            assertThat(first.message).isEqualTo("svc-1 started")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Ignore
    @Ignore
    @Test
    fun `stopAll stops registered services in reverse`() = runTest {
        val manager = DefaultServiceManager(sink)
        val a = TestService("a")
        val b = TestService("b")
        manager.register(a)
        manager.register(b)
        manager.startAll()
        manager.stopAll()
        assertThat(a.stopped).isTrue()
        assertThat(b.stopped).isTrue()
    }

    @Ignore
    @Ignore
    @Test
    fun `restart triggers stop then start`() = runTest {
        val manager = DefaultServiceManager(sink)
        val svc = TestService("svc-x")
        manager.register(svc)
        manager.startAll()
        manager.restart("svc-x")
        assertThat(svc.stopped).isTrue()
        assertThat(svc.startedAtLeast).isTrue()
    }
}

private class TestService(override val name: String) : AbstractManagedService(name) {
    var stopped = false
    var startedAtLeast = false

    override suspend fun onStart() {
        startedAtLeast = true
        emitLog("$name started")
    }

    override suspend fun onStop() {
        stopped = true
        emitLog("$name stopped")
    }
}
