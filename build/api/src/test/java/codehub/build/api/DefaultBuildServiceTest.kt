package codehub.build.api

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import codehub.core.diagnostics.InMemoryDiagnosticSink
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultBuildServiceTest {

    private val sink = InMemoryDiagnosticSink()

    @Test
    fun `enqueue with no provider emits Failed event`() = runTest {
        val service = DefaultBuildService(sink, emptyMap())
        val target = BuildTarget(
            id = "t1",
            displayName = "test",
            workspacePath = "/tmp",
            tool = BuildTool.Gradle,
            tasks = listOf("build")
        )
        service.events.test {
            val id = service.enqueue(target)
            assertThat(id).isEqualTo("t1")
            val queued = awaitItem()
            assertThat(queued).isInstanceOf(BuildEvent.Queued::class.java)
            val failed = awaitItem()
            assertThat(failed).isInstanceOf(BuildEvent.Failed::class.java)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `enqueue with available provider emits Started then Completed`() = runTest {
        val provider = FakeProvider(BuildTool.Ninja) { _, _ -> emptyList() }
        val service = DefaultBuildService(sink, mapOf(BuildTool.Ninja to provider))
        val target = BuildTarget(
            id = "t2",
            displayName = "ninja",
            workspacePath = "/tmp",
            tool = BuildTool.Ninja,
            tasks = listOf("all")
        )
        service.events.test {
            service.enqueue(target)
            awaitItem()
            val started = awaitItem()
            assertThat(started).isInstanceOf(BuildEvent.Started::class.java)
            val completed = awaitItem()
            assertThat(completed).isInstanceOf(BuildEvent.Completed::class.java)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

private class FakeProvider(
    override val tool: BuildTool,
    val onParse: (String, String) -> List<BuildDiagnostic>
) : BuildToolProvider {
    override suspend fun isAvailable(): Boolean = true

    override suspend fun execute(target: BuildTarget): BuildResult {
        return BuildResult(
            target = target,
            status = BuildStatus.Succeeded,
            exitCode = 0,
            stdout = "ok",
            stderr = "",
            durationMs = 10,
            artifacts = emptyList(),
            diagnostics = onParse("ok", ""),
            startedAt = System.currentTimeMillis(),
            finishedAt = System.currentTimeMillis()
        )
    }

    override fun parseDiagnostics(stdout: String, stderr: String): List<BuildDiagnostic> = onParse(stdout, stderr)
}
