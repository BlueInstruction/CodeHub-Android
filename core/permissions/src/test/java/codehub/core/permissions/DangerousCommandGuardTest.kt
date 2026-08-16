package codehub.core.permissions

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import codehub.core.diagnostics.InMemoryDiagnosticSink
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DangerousCommandGuardTest {

    private val sink = InMemoryDiagnosticSink()

    @Test
    fun `safe command is allowed immediately`() = runTest {
        val guard = DangerousCommandGuard(sink)
        val decision = guard.evaluate("ls -la /tmp", "session-1", "workspace-1")
        assertThat(decision.allowed).isTrue()
        assertThat(decision.decidedBy).isEqualTo("system")
    }

    @Test
    fun `rm -rf root blocks pending approval`() = runTest {
        val guard = DangerousCommandGuard(sink)
        val decision = guard.evaluate("rm -rf /", "session-1", "workspace-1")
        assertThat(decision.allowed).isFalse()
        assertThat(guard.pendingRequests.value).hasSize(1)
        val pending = guard.pendingRequests.value.first()
        assertThat(pending.command).isEqualTo("rm -rf /")
        assertThat(pending.match.severity).isEqualTo(DangerousCommandPatterns.Severity.Catastrophic)
    }

    @Test
    fun `resolve Once does not modify lists`() = runTest {
        val guard = DangerousCommandGuard(sink)
        val decision = guard.evaluate("git push --force origin main", "s1", "w1")
        assertThat(decision.allowed).isFalse()
        val requestId = guard.pendingRequests.value.first().requestId
        val resolved = guard.resolve(requestId, allowed = true, scope = DecisionScope.Once, decidedBy = "user")
        assertThat(resolved).isNotNull()
        assertThat(resolved!!.allowed).isTrue()
        guard.evaluate("git push --force origin main", "s2", "w1").also {
            assertThat(it.allowed).isFalse()
        }
    }

    @Test
    fun `resolve AlwaysAllow adds to allowlist`() = runTest {
        val guard = DangerousCommandGuard(sink)
        guard.evaluate("git reset --hard HEAD", "s1", "w1")
        val requestId = guard.pendingRequests.value.first().requestId
        guard.resolve(requestId, allowed = true, scope = DecisionScope.AlwaysAllow, decidedBy = "user")
        val again = guard.evaluate("git reset --hard HEAD~3", "s2", "w1")
        assertThat(again.allowed).isTrue()
        assertThat(again.reason).contains("allowlisted")
    }

    @Test
    fun `resolve AlwaysDeny adds to denylist`() = runTest {
        val guard = DangerousCommandGuard(sink)
        guard.evaluate("git push --force origin main", "s1", "w1")
        val requestId = guard.pendingRequests.value.first().requestId
        guard.resolve(requestId, allowed = false, scope = DecisionScope.AlwaysDeny, decidedBy = "user")
        val again = guard.evaluate("git push --force origin main", "s2", "w1")
        assertThat(again.allowed).isFalse()
        assertThat(again.reason).contains("denylisted")
    }

    @Test
    fun `decisionStream emits on resolve`() = runTest {
        val guard = DangerousCommandGuard(sink)
        guard.evaluate("rm -rf /", "s1", "w1")
        val requestId = guard.pendingRequests.value.first().requestId
        guard.decisionStream.test {
            guard.resolve(requestId, allowed = true, scope = DecisionScope.Once, decidedBy = "user")
            val decision = awaitItem()
            assertThat(decision.allowed).isTrue()
            assertThat(decision.decidedBy).isEqualTo("user")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
