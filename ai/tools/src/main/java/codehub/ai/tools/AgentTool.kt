package codehub.ai.tools

import kotlinx.serialization.Serializable

@Serializable
data class ToolParameter(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean,
    val default: String? = null
)

@Serializable
data class ToolDescriptor(
    val name: String,
    val description: String,
    val parameters: List<ToolParameter>,
    val requiresApproval: Boolean
)

@Serializable
data class ToolInvocation(
    val id: String,
    val toolName: String,
    val arguments: Map<String, String>,
    val workspaceId: String
)

@Serializable
data class ToolResult(
    val invocationId: String,
    val ok: Boolean,
    val output: String,
    val error: String? = null,
    val artifacts: List<String> = emptyList()
)

interface AgentTool {
    val descriptor: ToolDescriptor
    suspend fun invoke(invocation: ToolInvocation): ToolResult
}

object ToolRegistry {
    val builtinDescriptors: List<ToolDescriptor> = listOf(
        ToolDescriptor(
            name = "read_file",
            description = "Read the contents of a file at the given path.",
            parameters = listOf(
                ToolParameter("path", "string", "Absolute path to the file.", required = true)
            ),
            requiresApproval = false
        ),
        ToolDescriptor(
            name = "write_file",
            description = "Create or overwrite a file with the given content.",
            parameters = listOf(
                ToolParameter("path", "string", "Absolute path to the file.", required = true),
                ToolParameter("content", "string", "Full content to write.", required = true),
                ToolParameter("append", "boolean", "If true, append to existing content.", required = false, default = "false")
            ),
            requiresApproval = true
        ),
        ToolDescriptor(
            name = "delete_file",
            description = "Delete a file or directory.",
            parameters = listOf(
                ToolParameter("path", "string", "Absolute path to delete.", required = true),
                ToolParameter("recursive", "boolean", "Recursive deletion for directories.", required = false, default = "false")
            ),
            requiresApproval = true
        ),
        ToolDescriptor(
            name = "list_directory",
            description = "List entries in a directory.",
            parameters = listOf(
                ToolParameter("path", "string", "Absolute path to the directory.", required = true)
            ),
            requiresApproval = false
        ),
        ToolDescriptor(
            name = "search_code",
            description = "Search files matching a pattern.",
            parameters = listOf(
                ToolParameter("root", "string", "Root directory.", required = true),
                ToolParameter("pattern", "string", "Regex or substring.", required = true)
            ),
            requiresApproval = false
        ),
        ToolDescriptor(
            name = "search_symbols",
            description = "Look up symbols across the indexed project.",
            parameters = listOf(
                ToolParameter("workspace", "string", "Workspace root path.", required = true),
                ToolParameter("symbol", "string", "Symbol name prefix.", required = true)
            ),
            requiresApproval = false
        ),
        ToolDescriptor(
            name = "run_command",
            description = "Run a shell command.",
            parameters = listOf(
                ToolParameter("command", "string", "Full command line.", required = true),
                ToolParameter("cwd", "string", "Working directory.", required = true),
                ToolParameter("timeout_ms", "long", "Optional timeout in milliseconds.", required = false)
            ),
            requiresApproval = true
        ),
        ToolDescriptor(
            name = "run_build",
            description = "Run a build target.",
            parameters = listOf(
                ToolParameter("workspace", "string", "Workspace root.", required = true),
                ToolParameter("tool", "string", "gradle|cmake|ninja|clang.", required = true),
                ToolParameter("tasks", "string", "Comma-separated list of tasks.", required = true)
            ),
            requiresApproval = true
        ),
        ToolDescriptor(
            name = "read_build_log",
            description = "Read the most recent build log.",
            parameters = listOf(
                ToolParameter("workspace", "string", "Workspace root.", required = true)
            ),
            requiresApproval = false
        ),
        ToolDescriptor(
            name = "read_logcat",
            description = "Capture recent Logcat output.",
            parameters = listOf(
                ToolParameter("filter", "string", "Optional filter string.", required = false)
            ),
            requiresApproval = false
        ),
        ToolDescriptor(
            name = "inspect_vulkan",
            description = "Capture Vulkan device and instance info.",
            parameters = emptyList(),
            requiresApproval = false
        ),
        ToolDescriptor(
            name = "git_status",
            description = "Run git status in the workspace.",
            parameters = listOf(
                ToolParameter("workspace", "string", "Workspace root.", required = true)
            ),
            requiresApproval = false
        ),
        ToolDescriptor(
            name = "git_diff",
            description = "Run git diff in the workspace.",
            parameters = listOf(
                ToolParameter("workspace", "string", "Workspace root.", required = true),
                ToolParameter("staged", "boolean", "Whether to diff staged changes.", required = false, default = "false")
            ),
            requiresApproval = false
        ),
        ToolDescriptor(
            name = "git_log",
            description = "Show recent commits.",
            parameters = listOf(
                ToolParameter("workspace", "string", "Workspace root.", required = true),
                ToolParameter("limit", "int", "Number of commits.", required = false, default = "20")
            ),
            requiresApproval = false
        ),
        ToolDescriptor(
            name = "git_branch",
            description = "List, create, or switch branches.",
            parameters = listOf(
                ToolParameter("workspace", "string", "Workspace root.", required = true),
                ToolParameter("action", "string", "list|create|switch|delete.", required = true),
                ToolParameter("name", "string", "Branch name (when applicable).", required = false)
            ),
            requiresApproval = true
        ),
        ToolDescriptor(
            name = "git_commit",
            description = "Create a git commit.",
            parameters = listOf(
                ToolParameter("workspace", "string", "Workspace root.", required = true),
                ToolParameter("message", "string", "Commit message.", required = true),
                ToolParameter("add_all", "boolean", "Stage all changes first.", required = false, default = "false")
            ),
            requiresApproval = true
        ),
        ToolDescriptor(
            name = "git_push",
            description = "Push current branch to remote.",
            parameters = listOf(
                ToolParameter("workspace", "string", "Workspace root.", required = true),
                ToolParameter("remote", "string", "Remote name.", required = false, default = "origin")
            ),
            requiresApproval = true
        )
    )
}
