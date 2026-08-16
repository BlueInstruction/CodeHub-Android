package codehub.ai.tools.impl

import codehub.ai.tools.AgentTool
import codehub.ai.tools.ToolDescriptor
import codehub.ai.tools.ToolInvocation
import codehub.ai.tools.ToolResult
import codehub.ai.tools.ToolRegistry
import codehub.core.process.ProcessRunner
import codehub.core.process.ProcessSpec
import javax.inject.Inject

class RunCommandTool @Inject constructor(
    private val processRunner: ProcessRunner
) : AgentTool {

    override val descriptor: ToolDescriptor =
        ToolRegistry.builtinDescriptors.first { it.name == "run_command" }

    override suspend fun invoke(invocation: ToolInvocation): ToolResult {
        val command = invocation.arguments["command"]
            ?: return ToolResult(invocation.id, ok = false, output = "", error = "missing 'command'")
        val cwd = invocation.arguments["cwd"]
            ?: return ToolResult(invocation.id, ok = false, output = "", error = "missing 'cwd'")
        val timeout = invocation.arguments["timeout_ms"]?.toLongOrNull()
        val spec = ProcessSpec(
            command = listOf("/system/bin/sh", "-c", command),
            workingDirectory = cwd,
            environment = emptyMap(),
            timeoutMs = timeout
        )
        return runCatching {
            val result = processRunner.run(spec)
            val out = buildString {
                appendLine("exit=${result.exitCode} time=${result.durationMs}ms")
                appendLine("--- stdout ---")
                appendLine(result.stdout.trim().take(MAX_OUTPUT))
                appendLine("--- stderr ---")
                appendLine(result.stderr.trim().take(MAX_OUTPUT))
            }
            ToolResult(invocation.id, ok = result.exitCode == 0, output = out, error = if (result.exitCode != 0) "exit=${result.exitCode}" else null)
        }.getOrElse {
            ToolResult(invocation.id, ok = false, output = "", error = it.message)
        }
    }

    companion object {
        private const val MAX_OUTPUT = 8_000
    }
}
