package codehub.core.workspace

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import codehub.core.workspace.fs.JavaNioFileSystemGateway
import codehub.core.workspace.model.DirectoryListing
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileSystemGatewayTest {

    @get:Rule val tmp = TemporaryFolder()

    private val gateway = JavaNioFileSystemGateway()

    @Test
    fun `list returns directory entries sorted dirs first`() = runTest {
        val root = tmp.newFolder("list-test")
        File(root, "alpha.kt").writeText("fun main() {}")
        File(root, "beta.txt").writeText("hello")
        File(root, "subdir").mkdirs()

        val listing: DirectoryListing = gateway.list(root.absolutePath)
        assertThat(listing.entries).hasSize(3)
        assertThat(listing.entries.first().name).isEqualTo("subdir")
        assertThat(listing.entries.first().isDirectory).isTrue()
    }

    @Test
    fun `write then read round-trips content`() = runTest {
        val target = File(tmp.newFolder("rw"), "data.txt")
        gateway.write(target.absolutePath, "CodeHub".toByteArray())
        val read = gateway.read(target.absolutePath)
        assertThat(String(read)).isEqualTo("CodeHub")
    }

    @Test
    fun `delete removes file`() = runTest {
        val target = File(tmp.newFolder("rm"), "f.txt")
        target.writeText("x")
        gateway.delete(target.absolutePath, recursive = false)
        assertThat(target.exists()).isFalse()
    }

    @Test
    fun `move relocates file`() = runTest {
        val src = File(tmp.newFolder("mv"), "from.txt")
        val dst = File(tmp.newFolder("mv"), "to.txt")
        src.writeText("payload")
        gateway.move(src.absolutePath, dst.absolutePath)
        assertThat(src.exists()).isFalse()
        assertThat(dst.readText()).isEqualTo("payload")
    }
}
