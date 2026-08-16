package codehub.ai.context

import codehub.core.workspace.FileSystemGateway
import codehub.core.workspace.index.ProjectIndexer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultContextRetriever @Inject constructor(
    private val fs: FileSystemGateway,
    private val indexer: ProjectIndexer
) : ContextRetriever {

    private val scopes: List<ContextScope> = emptyList()

    override suspend fun retrieve(workspaceId: String, query: String): List<String> {
        val results = mutableListOf<String>()
        runCatching {
            val matches = indexer.searchFiles(workspaceId, query)
            matches.take(8).forEach { path ->
                val content = runCatching { String(fs.read(path)) }.getOrNull()
                if (content != null) {
                    val snippet = content.take(MAX_SNIPPET_CHARS)
                    results.add("File: $path\n$snippet")
                }
            }
        }
        runCatching {
            val symbols = indexer.lookupSymbol(workspaceId, query)
            symbols.take(4).forEach { path ->
                if (path !in results.map { it.substringAfter("File: ").substringBefore('\n') }) {
                    val content = runCatching { String(fs.read(path)) }.getOrNull()
                    if (content != null) {
                        results.add("File (symbol): $path\n${content.take(MAX_SNIPPET_CHARS)}")
                    }
                }
            }
        }
        scopes.forEach { scope ->
            runCatching { scope.gather(workspaceId, query) }.getOrNull()?.let { results.addAll(it) }
        }
        return results.take(MAX_CONTEXT_ITEMS)
    }

    override suspend fun refresh(workspaceId: String) {
        indexer.index(workspaceId)
    }

    companion object {
        private const val MAX_SNIPPET_CHARS = 1_500
        private const val MAX_CONTEXT_ITEMS = 8
    }
}
