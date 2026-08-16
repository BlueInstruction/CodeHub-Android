package codehub.core.process

import com.google.common.truth.Truth.assertThat
import codehub.core.diagnostics.InMemoryDiagnosticSink
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File

class JavaProcessRunnerTest {

    private val sink = InMemoryDiagnosticSink()
    private val runner = JavaProcessRunner(sink)

    @Test
    fun `run echo command produces zero exit code and stdout`() = runTest {
        val spec = ProcessSpec(
            command = if (File("/system/bin/echo").exists()) listOf("/system/bin/echo", "hello") else listOf("echo", "hello"),
            workingDirectory = System.getProperty("java.io.tmpdir"),
            environment = emptyMap(),
            timeoutMs = 5_000
        )
        val result = runner.run(spec)
        assertThat(result.exitCode).isEqualTo(0)
        assertThat(result.stdout.trim()).isEqualTo("hello")
    }

    @Test
    fun `run command with timeout kills the process`() = runTest {
        val spec = ProcessSpec(
            command = if (File("/system/bin/sleep").exists()) listOf("/system/bin/sleep", "30") else listOf("sleep", "30"),
            workingDirectory = System.getProperty("java.io.tmpdir"),
            environment = emptyMap(),
            timeoutMs = 200
        )
        val result = runner.run(spec)
        assertThat(result.timedOut).isTrue()
        assertThat(result.wasKilled).isTrue()
    }

    @Test
    fun `launch streams stdout then completes`() = runTest {
        val spec = ProcessSpec(
            command = if (File("/system/bin/echo").exists()) listOf("/system/bin/echo", "line1") else listOf("echo", "line1"),
            workingDirectory = System.getProperty("java.io.tmpdir"),
            environment = emptyMap(),
            timeoutMs = 5_000
        )
        val proc = runner.launch(spec)
        val firstLine = proc.stdout.first()
        assertThat(firstLine).isEqualTo("line1")
        val result = proc.await()
        assertThat(result.exitCode).isEqualTo(0)
    }
}
