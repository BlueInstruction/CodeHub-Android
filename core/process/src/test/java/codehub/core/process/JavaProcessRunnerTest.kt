package codehub.core.process

import com.google.common.truth.Truth.assertThat
import codehub.core.diagnostics.InMemoryDiagnosticSink
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Ignore
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

    @Ignore
    @Test
    fun `run command with timeout kills the process`() = runTest {
        val sleepBin = if (File("/system/bin/sleep").exists()) "/system/bin/sleep"
            else try { ProcessBuilder("which", "sleep").start().inputStream.bufferedReader().readText().trim() }
            catch (e: Exception) { "sleep" }
        if (sleepBin.isBlank()) return@runTest
        val spec = ProcessSpec(
            command = listOf(sleepBin, "30"),
            workingDirectory = System.getProperty("java.io.tmpdir"),
            environment = emptyMap(),
            timeoutMs = 500
        )
        val result = runner.run(spec)
        assertThat(result.timedOut).isTrue()
        assertThat(result.wasKilled).isTrue()
    }

    @Ignore
    @Test
    fun `launch streams stdout then completes`() = runTest {
        val echoBin = if (File("/system/bin/echo").exists()) "/system/bin/echo"
            else try { ProcessBuilder("which", "echo").start().inputStream.bufferedReader().readText().trim() }
            catch (e: Exception) { "echo" }
        if (echoBin.isBlank()) return@runTest
        val spec = ProcessSpec(
            command = listOf(echoBin, "line1"),
            workingDirectory = System.getProperty("java.io.tmpdir"),
            environment = emptyMap(),
            timeoutMs = 10_000
        )
        val proc = runner.launch(spec)
        val result = proc.await()
        assertThat(result.exitCode).isEqualTo(0)
        assertThat(result.stdout).contains("line1")
    }
}
