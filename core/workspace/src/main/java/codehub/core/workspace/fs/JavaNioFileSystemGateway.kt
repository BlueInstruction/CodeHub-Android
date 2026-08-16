package codehub.core.workspace.fs

import codehub.core.workspace.FileSystemGateway
import codehub.core.workspace.model.DirectoryListing
import codehub.core.workspace.model.FileEntry
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JavaNioFileSystemGateway @Inject constructor() : FileSystemGateway {

    override suspend fun list(directory: String): DirectoryListing {
        val dir = File(directory)
        if (!dir.exists() || !dir.isDirectory) {
            throw IOException("Not a directory: $directory")
        }
        val children = dir.listFiles().orEmpty()
        val entries = children.map { file ->
            val p = file.toPath()
            val linkTarget = if (Files.isSymbolicLink(p)) {
                Files.readSymbolicLink(p).toString()
            } else null
            FileEntry(
                path = file.absolutePath,
                name = file.name,
                isDirectory = file.isDirectory,
                size = if (file.isFile) file.length() else 0L,
                lastModified = file.lastModified(),
                readable = file.canRead(),
                writable = file.canWrite(),
                executable = file.canExecute(),
                symlinkTarget = linkTarget
            )
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        return DirectoryListing(path = dir.absolutePath, entries = entries)
    }

    override suspend fun read(path: String, offset: Long, limit: Long): ByteArray {
        val file = File(path)
        if (!file.isFile) throw IOException("Not a file: $path")
        file.inputStream().use { input ->
            if (offset > 0) {
                val skipped = input.skip(offset)
                if (skipped != offset) throw IOException("Could not seek to offset $offset")
            }
            val toRead = if (limit == Long.MAX_VALUE) (file.length() - offset).coerceAtLeast(0) else limit
            return input.readNBytes(toRead.toInt().coerceAtLeast(0))
        }
    }

    override suspend fun write(path: String, bytes: ByteArray, append: Boolean) {
        val file = File(path)
        file.parentFile?.mkdirs()
        file.outputStream().use { out ->
            if (append && file.exists()) out.write(file.readBytes())
            out.write(bytes)
        }
    }

    override suspend fun delete(path: String, recursive: Boolean) {
        val file = File(path)
        if (!file.exists()) return
        val ok = if (recursive) file.deleteRecursively() else file.delete()
        if (!ok) throw IOException("Failed to delete: $path")
    }

    override suspend fun stat(path: String): FileEntry? {
        val file = File(path)
        if (!file.exists()) return null
        return FileEntry(
            path = file.absolutePath,
            name = file.name,
            isDirectory = file.isDirectory,
            size = if (file.isFile) file.length() else 0L,
            lastModified = file.lastModified(),
            readable = file.canRead(),
            writable = file.canWrite(),
            executable = file.canExecute(),
            symlinkTarget = null
        )
    }

    override suspend fun createDirectory(path: String) {
        val created = File(path).mkdirs()
        if (!created && !File(path).isDirectory) {
            throw IOException("Failed to create directory: $path")
        }
    }

    override suspend fun move(from: String, to: String) {
        val source = File(from)
        val target = File(to)
        target.parentFile?.mkdirs()
        Files.move(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING
        )
    }

    override suspend fun copy(from: String, to: String) {
        val source = File(from)
        val target = File(to)
        target.parentFile?.mkdirs()
        Files.copy(
            source.toPath(),
            target.toPath(),
            LinkOption.NOFOLLOW_LINKS,
            StandardCopyOption.REPLACE_EXISTING
        )
    }

    override fun exists(path: String): Boolean = File(path).exists()
}
