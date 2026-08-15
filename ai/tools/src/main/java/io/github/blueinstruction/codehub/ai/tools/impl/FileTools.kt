package io.github.blueinstruction.codehub.ai.tools.impl

import io.github.blueinstruction.codehub.ai.tools.AgentTool
import io.github.blueinstruction.codehub.ai.tools.ToolDescriptor
import io.github.blueinstruction.codehub.ai.tools.ToolInvocation
import io.github.blueinstruction.codehub.ai.tools.ToolResult
import io.github.blueinstruction.codehub.ai.tools.ToolRegistry
import io.github.blueinstruction.codehub.core.workspace.FileSystemGateway
import javax.inject.Inject

class ReadFileTool @Inject constructor(
    private val fs: FileSystemGateway
) : AgentTool {

    override val descriptor: ToolDescriptor =
        ToolRegistry.builtinDescriptors.first { it.name == "read_file" }

    override suspend fun invoke(invocation: ToolInvocation): ToolResult {
        val path = invocation.arguments["path"]
            ?: return ToolResult(invocation.id, ok = false, output = "", error = "missing 'path'")
        return runCatching {
            val bytes = fs.read(path)
            ToolResult(invocation.id, ok = true, output = String(bytes), artifacts = listOf(path))
        }.getOrElse {
            ToolResult(invocation.id, ok = false, output = "", error = it.message)
        }
    }
}

class WriteFileTool @Inject constructor(
    private val fs: FileSystemGateway
) : AgentTool {

    override val descriptor: ToolDescriptor =
        ToolRegistry.builtinDescriptors.first { it.name == "write_file" }

    override suspend fun invoke(invocation: ToolInvocation): ToolResult {
        val path = invocation.arguments["path"]
            ?: return ToolResult(invocation.id, ok = false, output = "", error = "missing 'path'")
        val content = invocation.arguments["content"]
            ?: return ToolResult(invocation.id, ok = false, output = "", error = "missing 'content'")
        val append = invocation.arguments["append"]?.toBooleanStrictOrNull() ?: false
        return runCatching {
            fs.write(path, content.toByteArray(), append = append)
            ToolResult(invocation.id, ok = true, output = "wrote ${content.length} chars to $path", artifacts = listOf(path))
        }.getOrElse {
            ToolResult(invocation.id, ok = false, output = "", error = it.message)
        }
    }
}

class ListDirectoryTool @Inject constructor(
    private val fs: FileSystemGateway
) : AgentTool {

    override val descriptor: ToolDescriptor =
        ToolRegistry.builtinDescriptors.first { it.name == "list_directory" }

    override suspend fun invoke(invocation: ToolInvocation): ToolResult {
        val path = invocation.arguments["path"]
            ?: return ToolResult(invocation.id, ok = false, output = "", error = "missing 'path'")
        return runCatching {
            val listing = fs.list(path)
            val rendered = buildString {
                appendLine("${listing.entries.size} entries in $path")
                listing.entries.forEach { entry ->
                    val kind = if (entry.isDirectory) "DIR " else "FILE"
                    appendLine("$kind ${entry.name} (${entry.size} bytes)")
                }
            }
            ToolResult(invocation.id, ok = true, output = rendered, artifacts = listOf(path))
        }.getOrElse {
            ToolResult(invocation.id, ok = false, output = "", error = it.message)
        }
    }
}

class DeleteFileTool @Inject constructor(
    private val fs: FileSystemGateway
) : AgentTool {

    override val descriptor: ToolDescriptor =
        ToolRegistry.builtinDescriptors.first { it.name == "delete_file" }

    override suspend fun invoke(invocation: ToolInvocation): ToolResult {
        val path = invocation.arguments["path"]
            ?: return ToolResult(invocation.id, ok = false, output = "", error = "missing 'path'")
        val recursive = invocation.arguments["recursive"]?.toBooleanStrictOrNull() ?: false
        return runCatching {
            fs.delete(path, recursive = recursive)
            ToolResult(invocation.id, ok = true, output = "deleted $path", artifacts = listOf(path))
        }.getOrElse {
            ToolResult(invocation.id, ok = false, output = "", error = it.message)
        }
    }
}

class SearchCodeTool @Inject constructor(
    private val fs: FileSystemGateway
) : AgentTool {

    override val descriptor: ToolDescriptor =
        ToolRegistry.builtinDescriptors.first { it.name == "search_code" }

    override suspend fun invoke(invocation: ToolInvocation): ToolResult {
        val root = invocation.arguments["root"]
            ?: return ToolResult(invocation.id, ok = false, output = "", error = "missing 'root'")
        val pattern = invocation.arguments["pattern"]
            ?: return ToolResult(invocation.id, ok = false, output = "", error = "missing 'pattern'")
        return runCatching {
            val regex = runCatching { Regex(pattern) }.getOrElse { Regex(Regex.escape(pattern)) }
            val matches = mutableListOf<String>()
            walkAndSearch(java.io.File(root), regex, matches, maxDepth = 8)
            ToolResult(
                invocation.id,
                ok = true,
                output = if (matches.isEmpty()) "no matches" else matches.joinToString("\n"),
                artifacts = matches
            )
        }.getOrElse {
            ToolResult(invocation.id, ok = false, output = "", error = it.message)
        }
    }

    private fun walkAndSearch(dir: java.io.File, regex: Regex, sink: MutableList<String>, maxDepth: Int, depth: Int = 0) {
        if (depth > maxDepth) return
        if (!dir.exists() || !dir.isDirectory) return
        dir.listFiles().orEmpty().forEach { child ->
            if (child.isDirectory) {
                if (child.name !in SKIPPED_DIRS) walkAndSearch(child, regex, sink, maxDepth, depth + 1)
            } else if (child.length() < MAX_FILE_BYTES) {
                runCatching {
                    child.readText().lineSequence().forEachIndexed { i, line ->
                        if (regex.containsMatchIn(line)) {
                            sink.add("${child.absolutePath}:${i + 1}: ${line.trim().take(200)}")
                        }
                    }
                }
            }
        }
    }

    companion object {
        private val SKIPPED_DIRS = setOf(".git", "build", "node_modules", ".gradle", "target", ".idea", ".vscode")
        private const val MAX_FILE_BYTES = 512_000L
    }
}
