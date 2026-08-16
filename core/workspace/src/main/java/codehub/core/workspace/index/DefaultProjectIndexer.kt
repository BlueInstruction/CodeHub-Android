package codehub.core.workspace.index

import codehub.core.workspace.FileSystemGateway
import codehub.core.workspace.model.FileEntry
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

@Singleton
class DefaultProjectIndexer @Inject constructor(
    private val fs: FileSystemGateway
) : ProjectIndexer {

    private val snapshots = MutableSharedFlow<Pair<String, IndexSnapshot>>(extraBufferCapacity = 8)
    private val cache = mutableMapOf<String, IndexSnapshot>()

    private val binaryExtensions = setOf(
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "ico",
        "mp3", "mp4", "avi", "mov", "mkv", "flac", "ogg",
        "so", "dll", "exe", "bin", "dat", "pak", "dex",
        "zip", "tar", "gz", "bz2", "xz", "7z", "rar",
        "class", "o", "a", "obj"
    )

    private val symbolRegex = Regex(
        """\b(?:class|struct|enum|interface|fn|func|def|fun|object|trait|impl|public|private|static|final|inline|namespace|using)\s+([A-Za-z_][A-Za-z0-9_]*)"""
    )

    private val includeRegex = Regex(
        """^\s*#include\s+[<"]([^>"]+)[>"]"""
    )

    private val importRegex = Regex(
        """^\s*(?:import|using|from\s+\S+\s+import|require\(|require\s+)"""
    )

    override suspend fun index(rootPath: String): IndexSnapshot = withContext(Dispatchers.IO) {
        val files = ArrayList<IndexedFile>()
        walk(File(rootPath), files)
        val snapshot = IndexSnapshot(rootPath = rootPath, files = files, indexedAt = System.currentTimeMillis())
        cache[rootPath] = snapshot
        snapshots.tryEmit(rootPath to snapshot)
        snapshot
    }

    override fun observe(rootPath: String): Flow<IndexSnapshot> = kotlinx.coroutines.flow.flow {
        snapshots.asSharedFlow().collect { (_, snapshot) -> emit(snapshot) }
    }

    override suspend fun lookupSymbol(rootPath: String, symbol: String): List<String> {
        val snapshot = cache[rootPath] ?: index(rootPath)
        return snapshot.files.filter { symbol in it.symbols }.map { it.path }
    }

    override suspend fun searchFiles(rootPath: String, query: String): List<String> {
        val snapshot = cache[rootPath] ?: index(rootPath)
        return snapshot.files.filter { query.toRegex().containsMatchIn(File(it.path).name) }.map { it.path }
    }

    override suspend fun clear(rootPath: String) {
        cache.remove(rootPath)
    }

    private suspend fun walk(dir: File, sink: ArrayList<IndexedFile>) {
        if (!dir.exists()) return
        val listing = runCatching { fs.list(dir.absolutePath) }.getOrNull() ?: return
        listing.entries.forEach { entry ->
            val child = File(entry.path)
            if (entry.isDirectory) {
                if (child.name !in skippedDirs) walk(child, sink)
            } else if (entry.path.extensionLower() !in binaryExtensions && entry.size <= MAX_INDEXABLE_BYTES) {
                val indexed = indexFile(entry)
                if (indexed != null) sink.add(indexed)
            }
        }
    }

    private suspend fun indexFile(entry: FileEntry): IndexedFile? {
        val ext = entry.path.extensionLower()
        val language = languageForExtension(ext)
        if (language == "binary") return null
        val content = runCatching { fs.read(entry.path) }.getOrNull() ?: return null
        val text = String(content, Charsets.UTF_8)
        val symbols = symbolRegex.findAll(text).map { it.groupValues[1] }.distinct().toList()
        val imports = includeRegex.findAll(text).map { it.groupValues[1] }.toList() +
            importRegex.findAll(text).map { it.value.trim() }.toList()
        return IndexedFile(
            path = entry.path,
            language = language,
            symbols = symbols,
            imports = imports,
            sizeBytes = entry.size,
            modifiedAt = entry.lastModified
        )
    }

    private fun languageForExtension(ext: String): String = when (ext) {
        "kt", "kts" -> "kotlin"
        "java" -> "java"
        "c", "h" -> "c"
        "cpp", "cc", "cxx", "hpp", "hxx" -> "cpp"
        "rs" -> "rust"
        "py" -> "python"
        "js", "mjs" -> "javascript"
        "ts" -> "typescript"
        "go" -> "go"
        "cmake" -> "cmake"
        "gradle" -> "gradle-groovy"
        "xml" -> "xml"
        "json" -> "json"
        "toml" -> "toml"
        "yaml", "yml" -> "yaml"
        "md" -> "markdown"
        "sh", "bash" -> "shell"
        "spv" -> "spirv"
        "glsl", "vert", "frag" -> "glsl"
        else -> if (ext.isEmpty()) "binary" else "unknown"
    }

    private fun String.extensionLower(): String = substringAfterLast('.', "").lowercase()

    companion object {
        private const val MAX_INDEXABLE_BYTES = 2L * 1024 * 1024
        private val skippedDirs = setOf(
            ".git", ".hg", ".svn", "build", ".gradle", ".idea", ".vscode",
            "node_modules", "target", "out", "bin", "obj", "__pycache__",
            ".cxx", ".externalNativeBuild", ".cache", ".dart_tool"
        )
    }
}
