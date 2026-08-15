package io.github.blueinstruction.codehub.core.workspace

import io.github.blueinstruction.codehub.core.workspace.model.DirectoryListing
import io.github.blueinstruction.codehub.core.workspace.model.FileEntry
import java.io.IOException

interface FileSystemGateway {
    @Throws(IOException::class)
    suspend fun list(directory: String): DirectoryListing

    @Throws(IOException::class)
    suspend fun read(path: String, offset: Long = 0, limit: Long = Long.MAX_VALUE): ByteArray

    @Throws(IOException::class)
    suspend fun write(path: String, bytes: ByteArray, append: Boolean = false)

    @Throws(IOException::class)
    suspend fun delete(path: String, recursive: Boolean = false)

    @Throws(IOException::class)
    suspend fun stat(path: String): FileEntry?

    @Throws(IOException::class)
    suspend fun createDirectory(path: String)

    @Throws(IOException::class)
    suspend fun move(from: String, to: String)

    @Throws(IOException::class)
    suspend fun copy(from: String, to: String)

    fun exists(path: String): Boolean
}
