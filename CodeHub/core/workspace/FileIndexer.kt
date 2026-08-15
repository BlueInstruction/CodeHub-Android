package com.codehub.core.workspace

data class FileMetadata(
    val path: String,
    val name: String,
    val size: Long,
    val lastModified: Long = System.currentTimeMillis(),
    val isDirectory: Boolean = false,
    val contentType: String? = null
)

interface FileIndexer {
    fun listFiles(dir: String): List<FileMetadata>
    fun readFile(path: String): String?
    fun writeFile(path: String, content: String): Boolean
    fun deleteFile(path: String): Boolean
    fun createDirectory(path: String): Boolean
}