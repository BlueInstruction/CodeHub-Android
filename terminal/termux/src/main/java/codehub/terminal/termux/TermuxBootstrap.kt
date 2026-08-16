package codehub.terminal.termux

import android.content.Context
import codehub.core.diagnostics.DiagnosticEvent
import codehub.core.diagnostics.DiagnosticEventKind
import codehub.core.diagnostics.DiagnosticSeverity
import codehub.core.diagnostics.DiagnosticSink
import codehub.core.diagnostics.DiagnosticStatus
import codehub.core.process.ProcessRunner
import codehub.core.process.ProcessSpec
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable

@Serializable
data class TermuxReadiness(
    val termuxInstalled: Boolean,
    val termuxHomePath: String,
    val termuxPrefixPath: String,
    val availableTools: Map<TermuxTool, Boolean>,
    val missingTools: List<TermuxTool>,
    val issues: List<String>,
    val ready: Boolean
)

@Serializable
enum class TermuxTool {
    Bash, Git, Make, Cmake, Ninja, Clang, Python, Node, Java, Adb, Openssh, Curl, Wget, Tar, Rsync, Vim, Nano
}

@Singleton
class TermuxBootstrap @Inject constructor(
    private val processRunner: ProcessRunner,
    private val diagnostics: DiagnosticSink,
    private val context: Context
) {

    private val termuxHome = "/data/data/com.termux/files/home"
    private val termuxPrefix = "/data/data/com.termux/files/usr"
    private val termuxBin = "$termuxPrefix/bin"

    suspend fun probe(): TermuxReadiness {
        val installed = isTermuxInstalled()
        val issues = mutableListOf<String>()

        if (!installed) {
            issues += "Termux is not installed. Install from F-Droid or the Termux GitHub releases."
            return TermuxReadiness(
                termuxInstalled = false,
                termuxHomePath = termuxHome,
                termuxPrefixPath = termuxPrefix,
                availableTools = emptyMap(),
                missingTools = TermuxTool.values().toList(),
                issues = issues,
                ready = false
            )
        }

        if (!File(termuxHome).isDirectory) {
            issues += "Termux home directory $termuxHome is not accessible."
        }
        if (!File(termuxBin).isDirectory) {
            issues += "Termux bin directory $termuxBin is not accessible."
        }

        val toolResults = mutableMapOf<TermuxTool, Boolean>()
        TermuxTool.values().forEach { tool ->
            val present = checkTool(tool)
            toolResults[tool] = present
            if (!present) issues += "Missing tool: ${tool.name.lowercase()}"
        }

        val missing = toolResults.filter { !it.value }.keys.toList()
        val ready = issues.isEmpty() && missing.none { it in CRITICAL_TOOLS }

        diagnostics.emit(
            DiagnosticEvent.now(
                kind = DiagnosticEventKind.RuntimeInitialization,
                severity = if (ready) DiagnosticSeverity.Info else DiagnosticSeverity.Warn,
                status = if (ready) DiagnosticStatus.Ok else DiagnosticStatus.Skipped,
                source = "TermuxBootstrap",
                message = if (ready) "Termux ready" else "Termux missing: ${missing.joinToString(",")}",
                reason = if (ready) "all_critical_tools_present" else "missing_tools",
                attributes = toolResults.map { it.key.name to it.value.toString() }
            )
        )

        return TermuxReadiness(
            termuxInstalled = true,
            termuxHomePath = termuxHome,
            termuxPrefixPath = termuxPrefix,
            availableTools = toolResults,
            missingTools = missing,
            issues = issues,
            ready = ready
        )
    }

    suspend fun installPackages(tool: List<TermuxTool>): Boolean {
        if (!isTermuxInstalled()) return false
        val pkgNames = tool.map { it.packageName() }.filter { it.isNotBlank() }
        if (pkgNames.isEmpty()) return false
        val spec = ProcessSpec(
            command = listOf("$termuxBin/pkg", "install", "-y") + pkgNames,
            workingDirectory = termuxHome,
            environment = mapOf(
                "HOME" to termuxHome,
                "PREFIX" to termuxPrefix,
                "PATH" to "$termuxBin:/system/bin"
            ),
            timeoutMs = 5L * 60 * 1000
        )
        val result = processRunner.run(spec)
        return result.exitCode == 0
    }

    fun buildEnvironment(): Map<String, String> = mapOf(
        "HOME" to termuxHome,
        "PREFIX" to termuxPrefix,
        "PATH" to "$termuxBin:$termuxPrefix/bin:/system/bin:/system/xbin",
        "LD_LIBRARY_PATH" to "$termuxPrefix/lib",
        "LANG" to "en_US.UTF-8",
        "TERM" to "xterm-256color",
        "TMPDIR" to "$termuxPrefix/tmp",
        "ANDROID_DATA" to "/data",
        "ANDROID_ROOT" to "/system"
    )

    private fun isTermuxInstalled(): Boolean {
        val pkgInfo = runCatching {
            context.packageManager.getApplicationInfo("com.termux", 0)
        }.getOrNull()
        if (pkgInfo != null) return true
        return File("/data/data/com.termux").isDirectory
    }

    private suspend fun checkTool(tool: TermuxTool): Boolean {
        val binaryName = tool.binaryName()
        val localPath = File(termuxBin, binaryName)
        if (localPath.exists() && localPath.canExecute()) return true
        val whichSpec = ProcessSpec(
            command = listOf("which", binaryName),
            workingDirectory = termuxHome,
            environment = buildEnvironment(),
            timeoutMs = 1_500
        )
        val result = runCatching { processRunner.run(whichSpec) }.getOrNull()
        return result != null && result.exitCode == 0 && result.stdout.isNotBlank()
    }

    private fun TermuxTool.binaryName(): String = when (this) {
        TermuxTool.Bash -> "bash"
        TermuxTool.Git -> "git"
        TermuxTool.Make -> "make"
        TermuxTool.Cmake -> "cmake"
        TermuxTool.Ninja -> "ninja"
        TermuxTool.Clang -> "clang"
        TermuxTool.Python -> "python"
        TermuxTool.Node -> "node"
        TermuxTool.Java -> "java"
        TermuxTool.Adb -> "adb"
        TermuxTool.Openssh -> "ssh"
        TermuxTool.Curl -> "curl"
        TermuxTool.Wget -> "wget"
        TermuxTool.Tar -> "tar"
        TermuxTool.Rsync -> "rsync"
        TermuxTool.Vim -> "vim"
        TermuxTool.Nano -> "nano"
    }

    private fun TermuxTool.packageName(): String = when (this) {
        TermuxTool.Bash -> "bash"
        TermuxTool.Git -> "git"
        TermuxTool.Make -> "make"
        TermuxTool.Cmake -> "cmake"
        TermuxTool.Ninja -> "ninja"
        TermuxTool.Clang -> "clang"
        TermuxTool.Python -> "python"
        TermuxTool.Node -> "nodejs"
        TermuxTool.Java -> "openjdk-17"
        TermuxTool.Adb -> "android-tools"
        TermuxTool.Openssh -> "openssh"
        TermuxTool.Curl -> "curl"
        TermuxTool.Wget -> "wget"
        TermuxTool.Tar -> "tar"
        TermuxTool.Rsync -> "rsync"
        TermuxTool.Vim -> "vim"
        TermuxTool.Nano -> "nano"
    }

    companion object {
        val CRITICAL_TOOLS = setOf(TermuxTool.Bash, TermuxTool.Git, TermuxTool.Cmake, TermuxTool.Ninja, TermuxTool.Clang)
    }
}
