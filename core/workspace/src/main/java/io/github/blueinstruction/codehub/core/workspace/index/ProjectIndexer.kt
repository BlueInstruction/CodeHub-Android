package io.github.blueinstruction.codehub.core.workspace.index

import kotlinx.coroutines.flow.Flow

data class IndexedFile(
    val path: String,
    val language: String,
    val symbols: List<String>,
    val imports: List<String>,
    val sizeBytes: Long,
    val modifiedAt: Long
)

data class IndexSnapshot(
    val rootPath: String,
    val files: List<IndexedFile>,
    val indexedAt: Long
) {
    val fileCount: Int get() = files.size
}

interface ProjectIndexer {
    suspend fun index(rootPath: String): IndexSnapshot
    fun observe(rootPath: String): Flow<IndexSnapshot>
    suspend fun lookupSymbol(rootPath: String, symbol: String): List<String>
    suspend fun searchFiles(rootPath: String, query: String): List<String>
    suspend fun clear(rootPath: String)
}
