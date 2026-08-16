package codehub.core.permissions

import kotlinx.serialization.Serializable

@Serializable
enum class PermissionLevel {
    READ_ONLY,
    WORKSPACE_WRITE,
    BUILD,
    GIT_WRITE,
    FULL_AUTONOMY
}

@Serializable
data class ToolRequest(
    val toolName: String,
    val arguments: Map<String, String>,
    val requestedLevel: PermissionLevel,
    val workspaceId: String,
    val agentSessionId: String
)

@Serializable
data class PermissionDecision(
    val request: ToolRequest,
    val granted: Boolean,
    val grantLevel: PermissionLevel?,
    val reason: String?,
    val decidedBy: String
)

@Serializable
data class AgentPolicy(
    val agentId: String,
    val defaultLevel: PermissionLevel,
    val allowedTools: Set<String>,
    val deniedTools: Set<String>,
    val requireApprovalForDestructive: Boolean = true,
    val workspaceScope: String?
)

object PermissionPolicy {
    val destructiveTools = setOf(
        "delete_file",
        "git_push",
        "git_commit",
        "run_command",
        "run_build",
        "install_package"
    )

    val levelToolMatrix: Map<PermissionLevel, Set<String>> = mapOf(
        PermissionLevel.READ_ONLY to setOf(
            "read_file", "list_directory", "search_code", "search_symbols",
            "git_status", "git_diff", "git_log", "read_build_log",
            "read_logcat", "inspect_vulkan"
        ),
        PermissionLevel.WORKSPACE_WRITE to setOf(
            "write_file", "create_file", "move_file", "rename_file"
        ),
        PermissionLevel.BUILD to setOf("run_build", "read_build_log"),
        PermissionLevel.GIT_WRITE to setOf("git_commit", "git_push", "git_branch", "git_tag"),
        PermissionLevel.FULL_AUTONOMY to setOf("*")
    )

    fun levelAllows(level: PermissionLevel, tool: String): Boolean {
        val tools = levelToolMatrix[level] ?: return false
        return "*" in tools || tool in tools
    }

    fun isDestructive(tool: String): Boolean = tool in destructiveTools
}
