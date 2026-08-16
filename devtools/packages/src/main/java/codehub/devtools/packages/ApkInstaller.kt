package codehub.devtools.packages

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
data class ApkInstallResult(
    val success: Boolean,
    val apkPath: String,
    val packageName: String?,
    val exitCode: Int,
    val output: String,
    val failureReason: String? = null,
    val durationMs: Long
)

@Serializable
data class ApkDescriptor(
    val path: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val sizeBytes: Long
)

@Singleton
class ApkInstaller @Inject constructor(
    private val processRunner: ProcessRunner,
    private val diagnostics: DiagnosticSink,
    private val packageInspector: PackageInspector
) {

    suspend fun install(apkPath: String, reinstall: Boolean = false, downgrade: Boolean = false): ApkInstallResult {
        val started = System.currentTimeMillis()
        val apkFile = File(apkPath)
        if (!apkFile.exists() || !apkFile.isFile || !apkPath.endsWith(".apk", ignoreCase = true)) {
            val result = ApkInstallResult(
                success = false,
                apkPath = apkPath,
                packageName = null,
                exitCode = -1,
                output = "",
                failureReason = "APK file not found or invalid: $apkPath",
                durationMs = System.currentTimeMillis() - started
            )
            diagnostics.emit(
                DiagnosticEvent.now(
                    kind = DiagnosticEventKind.RuntimeInitialization,
                    severity = DiagnosticSeverity.Error,
                    status = DiagnosticStatus.Failed,
                    source = "ApkInstaller",
                    message = "Install failed: ${result.failureReason}"
                )
            )
            return result
        }

        val args = mutableListOf("pm", "install")
        if (reinstall) args += "-r"
        if (downgrade) args += "-d"
        args += apkPath

        val spec = ProcessSpec(
            command = args,
            workingDirectory = "/",
            environment = emptyMap(),
            timeoutMs = 2L * 60 * 1000
        )
        val result = processRunner.run(spec)
        val success = result.exitCode == 0 && result.stdout.contains("Success", ignoreCase = true)
        val packageName = extractPackageName(result.stdout, apkPath)

        if (success) {
            diagnostics.emit(
                DiagnosticEvent.now(
                    kind = DiagnosticEventKind.RuntimeInitialization,
                    severity = DiagnosticSeverity.Info,
                    status = DiagnosticStatus.Ok,
                    source = "ApkInstaller",
                    message = "APK installed: $apkPath",
                    attributes = mapOf(
                        "package" to (packageName ?: ""),
                        "size_bytes" to apkFile.length().toString()
                    )
                )
            )
        } else {
            diagnostics.emit(
                DiagnosticEvent.now(
                    kind = DiagnosticEventKind.RuntimeInitialization,
                    severity = DiagnosticSeverity.Warn,
                    status = DiagnosticStatus.Failed,
                    source = "ApkInstaller",
                    message = "APK install failed",
                    reason = result.stdout.take(500),
                    attributes = mapOf("apk" to apkPath)
                )
            )
        }

        return ApkInstallResult(
            success = success,
            apkPath = apkPath,
            packageName = packageName,
            exitCode = result.exitCode,
            output = result.stdout,
            failureReason = if (!success) result.stdout.trim().take(500) else null,
            durationMs = System.currentTimeMillis() - started
        )
    }

    suspend fun uninstall(packageName: String): Boolean {
        val spec = ProcessSpec(
            command = listOf("pm", "uninstall", packageName),
            workingDirectory = "/",
            environment = emptyMap(),
            timeoutMs = 30_000
        )
        val result = processRunner.run(spec)
        return result.exitCode == 0 && result.stdout.contains("Success", ignoreCase = true)
    }

    suspend fun launch(packageName: String): Boolean {
        val intent = packageInspector.launch(packageName)
        return intent
    }

    suspend fun discoverApks(workspacePath: String): List<ApkDescriptor> {
        val root = File(workspacePath)
        if (!root.exists() || !root.isDirectory) return emptyList()
        return root.walkTopDown()
            .filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
            .map { describeApk(it) }
            .filter { it != null }
            .toList()
            .mapNotNull { it }
    }

    private fun describeApk(file: File): ApkDescriptor? {
        val spec = ProcessSpec(
            command = listOf("dumpsys", "package", file.absolutePath),
            workingDirectory = "/",
            environment = emptyMap(),
            timeoutMs = 5_000
        )
        val result = runCatching { kotlinx.coroutines.runBlocking { processRunner.run(spec) } }.getOrNull()
            ?: return null
        val packageName = Regex("""packageName=([^\s]+)""").find(result.stdout)?.groups?.get(1)?.value
            ?: return null
        val versionName = Regex("""versionName=([^\s]+)""").find(result.stdout)?.groups?.get(1)?.value ?: ""
        val versionCode = Regex("""versionCode=(\d+)""").find(result.stdout)?.groups?.get(1)?.value?.toLongOrNull() ?: 0L
        val minSdk = Regex("""minSdk=(\d+)""").find(result.stdout)?.groups?.get(1)?.value?.toIntOrNull() ?: 0
        val targetSdk = Regex("""targetSdk=(\d+)""").find(result.stdout)?.groups?.get(1)?.value?.toIntOrNull() ?: 0
        return ApkDescriptor(
            path = file.absolutePath,
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            minSdk = minSdk,
            targetSdk = targetSdk,
            sizeBytes = file.length()
        )
    }

    private fun extractPackageName(installOutput: String, apkPath: String): String? {
        return Regex("""pkg:\s*([^\s]+)""").find(installOutput)?.groups?.get(1)?.value
            ?: Regex("""package\s+([a-zA-Z0-9_.]+)""").find(installOutput)?.groups?.get(1)?.value
            ?: runCatching {
                val spec = ProcessSpec(
                    command = listOf("dumpsys", "package", apkPath),
                    workingDirectory = "/",
                    environment = emptyMap(),
                    timeoutMs = 5_000
                )
                val r = kotlinx.coroutines.runBlocking { processRunner.run(spec) }
                Regex("""packageName=([^\s]+)""").find(r.stdout)?.groups?.get(1)?.value
            }.getOrNull()
    }
}
