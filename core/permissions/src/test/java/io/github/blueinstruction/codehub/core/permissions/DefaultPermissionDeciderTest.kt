package io.github.blueinstruction.codehub.core.permissions

import com.google.common.truth.Truth.assertThat
import io.github.blueinstruction.codehub.core.diagnostics.DiagnosticSink
import io.github.blueinstruction.codehub.core.diagnostics.InMemoryDiagnosticSink
import org.junit.Test

class DefaultPermissionDeciderTest {

    private val sink: DiagnosticSink = InMemoryDiagnosticSink()
    private val decider = DefaultPermissionDecider(sink)

    @Test
    fun `read-only tool granted when policy allows`() {
        decider.setAgentPolicy(
            AgentPolicy(
                agentId = "agent-1",
                defaultLevel = PermissionLevel.READ_ONLY,
                allowedTools = setOf("read_file", "list_directory"),
                deniedTools = emptySet(),
                requireApprovalForDestructive = false,
                workspaceScope = "ws-1"
            )
        )
        val request = ToolRequest(
            toolName = "read_file",
            arguments = mapOf("path" to "/x"),
            requestedLevel = PermissionLevel.READ_ONLY,
            workspaceId = "ws-1",
            agentSessionId = "agent-1"
        )
        val decision = decider.evaluate(request)
        assertThat(decision.granted).isTrue()
    }

    @Test
    fun `destructive tool triggers pending approval`() {
        decider.setAgentPolicy(
            AgentPolicy(
                agentId = "agent-2",
                defaultLevel = PermissionLevel.GIT_WRITE,
                allowedTools = setOf("git_commit", "git_push"),
                deniedTools = emptySet(),
                requireApprovalForDestructive = true,
                workspaceScope = "ws-2"
            )
        )
        val request = ToolRequest(
            toolName = "git_commit",
            arguments = mapOf("message" to "x"),
            requestedLevel = PermissionLevel.GIT_WRITE,
            workspaceId = "ws-2",
            agentSessionId = "agent-2"
        )
        val decision = decider.evaluate(request)
        assertThat(decision.granted).isFalse()
        assertThat(decision.reason).contains("Pending")
    }

    @Test
    fun `resolve flips pending to granted`() = kotlinx.coroutines.test.runTest {
        decider.setAgentPolicy(
            AgentPolicy(
                agentId = "agent-3",
                defaultLevel = PermissionLevel.WORKSPACE_WRITE,
                allowedTools = setOf("delete_file"),
                deniedTools = emptySet(),
                requireApprovalForDestructive = true,
                workspaceScope = "ws-3"
            )
        )
        val request = ToolRequest(
            toolName = "delete_file",
            arguments = mapOf("path" to "/x", "__requestId" to "req-1"),
            requestedLevel = PermissionLevel.WORKSPACE_WRITE,
            workspaceId = "ws-3",
            agentSessionId = "agent-3"
        )
        decider.evaluate(request)
        val resolved = decider.resolve("req-1", granted = true, decidedBy = "user")
        assertThat(resolved).isNotNull()
        assertThat(resolved!!.granted).isTrue()
        assertThat(resolved.decidedBy).isEqualTo("user")
    }

    @Test
    fun `tool not in allowedTools is blocked`() {
        decider.setAgentPolicy(
            AgentPolicy(
                agentId = "agent-4",
                defaultLevel = PermissionLevel.READ_ONLY,
                allowedTools = setOf("read_file"),
                deniedTools = emptySet(),
                requireApprovalForDestructive = false,
                workspaceScope = "ws-4"
            )
        )
        val request = ToolRequest(
            toolName = "run_command",
            arguments = emptyMap(),
            requestedLevel = PermissionLevel.BUILD,
            workspaceId = "ws-4",
            agentSessionId = "agent-4"
        )
        val decision = decider.evaluate(request)
        assertThat(decision.granted).isFalse()
    }
}
