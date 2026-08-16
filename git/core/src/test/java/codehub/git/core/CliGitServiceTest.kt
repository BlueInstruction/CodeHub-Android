package codehub.git.core

import com.google.common.truth.Truth.assertThat
import codehub.core.diagnostics.InMemoryDiagnosticSink
import codehub.core.process.JavaProcessRunner
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CliGitServiceTest {

    @get:Rule val tmp = TemporaryFolder()

    private val sink = InMemoryDiagnosticSink()
    private val runner = JavaProcessRunner(sink)
    private val git = CliGitService(runner, sink)

    private fun initRepo(): File {
        val dir = tmp.newFolder("repo")
        runCommand(dir, listOf("git", "init", "-b", "main"))
        runCommand(dir, listOf("git", "config", "user.email", "test@example.com"))
        runCommand(dir, listOf("git", "config", "user.name", "Tester"))
        return dir
    }

    private fun runCommand(dir: File, cmd: List<String>): String {
        val process = ProcessBuilder(cmd).directory(dir).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        return output
    }

    @Test
    fun `status of fresh repo is clean`() = runTest {
        val repo = initRepo()
        val status = git.status(repo.absolutePath)
        assertThat(status.branch).isEqualTo("main")
        assertThat(status.clean).isTrue()
    }

    @Test
    fun `commit creates a new commit and shows in log`() = runTest {
        val repo = initRepo()
        File(repo, "README.md").writeText("hello")
        git.commit(repo.absolutePath, "Initial commit", addAll = true)
        val log = git.log(repo.absolutePath, limit = 5)
        assertThat(log).hasSize(1)
        assertThat(log.first().message).isEqualTo("Initial commit")
    }

    @Test
    fun `branch list shows created branch`() = runTest {
        val repo = initRepo()
        File(repo, "f.txt").writeText("x")
        git.commit(repo.absolutePath, "init", addAll = true)
        git.createBranch(repo.absolutePath, "feature")
        val branches = git.branches(repo.absolutePath)
        assertThat(branches.map { it.name }).contains("feature")
        assertThat(branches.map { it.name }).contains("main")
    }

    @Test
    fun `status detects untracked files`() = runTest {
        val repo = initRepo()
        File(repo, "new.txt").writeText("untracked")
        val status = git.status(repo.absolutePath)
        assertThat(status.untracked).contains("new.txt")
        assertThat(status.clean).isFalse()
    }
}
