package codehub.build.toolchain

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

@Singleton
class ToolchainInstaller @Inject constructor(
    private val processRunner: ProcessRunner,
    private val diagnostics: DiagnosticSink,
    private val context: Context,
    private val toolchainManager: ToolchainManager
) {

    private val termuxHome = "/data/data/com.termux/files/home"
    private val termuxPrefix = "/data/data/com.termux/files/usr"

    suspend fun installTermuxPackages(packages: List<String>): Boolean {
        if (packages.isEmpty()) return true
        diagnostics.emit(
            DiagnosticEvent.now(
                kind = DiagnosticEventKind.RuntimeInitialization,
                severity = DiagnosticSeverity.Info,
                status = DiagnosticStatus.Pending,
                source = "ToolchainInstaller",
                message = "Installing Termux packages: ${packages.joinToString(", ")}",
                attributes = mapOf("packages" to packages.joinToString(","))
            )
        )
        val spec = ProcessSpec(
            command = listOf("$termuxPrefix/bin/pkg", "install", "-y") + packages,
            workingDirectory = termuxHome,
            environment = mapOf(
                "HOME" to termuxHome,
                "PREFIX" to termuxPrefix,
                "PATH" to "$termuxPrefix/bin:/system/bin"
            ),
            timeoutMs = 10L * 60 * 1000
        )
        val result = processRunner.run(spec)
        val ok = result.exitCode == 0
        diagnostics.emit(
            DiagnosticEvent.now(
                kind = DiagnosticEventKind.RuntimeInitialization,
                severity = if (ok) DiagnosticSeverity.Info else DiagnosticSeverity.Error,
                status = if (ok) DiagnosticStatus.Ok else DiagnosticStatus.Failed,
                source = "ToolchainInstaller",
                message = if (ok) "Termux packages installed: ${packages.joinToString(", ")}"
                    else "Failed to install Termux packages: ${result.stderr.take(300)}",
                attributes = mapOf("exit_code" to result.exitCode.toString())
            )
        )
        return ok
    }

    suspend fun provisionAndroidSdk(neededComponents: List<String> = emptyList()): Boolean {
        val codehubSdk = File(context.filesDir, "android-sdk")
        codehubSdk.mkdirs()
        val cmdlineToolsDir = File(codehubSdk, "cmdline-tools/latest")
        cmdlineToolsDir.mkdirs()

        diagnostics.emit(
            DiagnosticEvent.now(
                kind = DiagnosticEventKind.RuntimeInitialization,
                severity = DiagnosticSeverity.Info,
                status = DiagnosticStatus.Pending,
                source = "ToolchainInstaller",
                message = "Provisioning Android SDK at ${codehubSdk.absolutePath}"
            )
        )

        val sdkmanager = File(cmdlineToolsDir, "bin/sdkmanager")
        if (!sdkmanager.exists()) {
            val downloaded = downloadCmdlineTools(cmdlineToolsDir)
            if (!downloaded) return false
        }

        val allPackages = listOf(
            "platform-tools",
            "build-tools;35.0.0",
            "platforms;android-35",
            "platforms;android-34",
            "ndk;27.0.12077973"
        )

        val packagesToInstall = if (neededComponents.isEmpty()) {
            allPackages
        } else {
            allPackages.filter { pkg ->
                neededComponents.any { needed ->
                    pkg.startsWith(needed, ignoreCase = true) ||
                        needed.equals("AndroidSdk", ignoreCase = true) ||
                        (needed.equals("PlatformTools", ignoreCase = true) && pkg == "platform-tools") ||
                        (needed.equals("BuildTools", ignoreCase = true) && pkg.startsWith("build-tools")) ||
                        (needed.equals("PlatformSdk", ignoreCase = true) && pkg.startsWith("platforms;")) ||
                        (needed.equals("Ndk", ignoreCase = true) && pkg.startsWith("ndk;"))
                }
            }
        }

        val env = mapOf(
            "ANDROID_HOME" to codehubSdk.absolutePath,
            "ANDROID_SDK_ROOT" to codehubSdk.absolutePath,
            "HOME" to termuxHome,
            "PATH" to "$termuxPrefix/bin:/system/bin"
        )
        var allOk = true
        for (pkg in packagesToInstall) {
            if (isSdkPackageInstalled(codehubSdk, pkg)) {
                diagnostics.emit(
                    DiagnosticEvent.now(
                        kind = DiagnosticEventKind.RuntimeInitialization,
                        severity = DiagnosticSeverity.Info,
                        status = DiagnosticStatus.Ok,
                        source = "ToolchainInstaller",
                        message = "SDK package already installed: $pkg (skipping)"
                    )
                )
                continue
            }
            val spec = ProcessSpec(
                command = listOf("$cmdlineToolsDir/bin/sdkmanager", "--licenses", pkg),
                workingDirectory = codehubSdk.absolutePath,
                environment = env,
                timeoutMs = 10L * 60 * 1000
            )
            val result = processRunner.run(spec)
            if (result.exitCode != 0) {
                allOk = false
                diagnostics.emit(
                    DiagnosticEvent.now(
                        kind = DiagnosticEventKind.RuntimeInitialization,
                        severity = DiagnosticSeverity.Warn,
                        status = DiagnosticStatus.Failed,
                        source = "ToolchainInstaller",
                        message = "Failed to install $pkg: ${result.stderr.take(300)}"
                    )
                )
            }
        }

        diagnostics.emit(
            DiagnosticEvent.now(
                kind = DiagnosticEventKind.RuntimeInitialization,
                severity = DiagnosticSeverity.Info,
                status = DiagnosticStatus.Ok,
                source = "ToolchainInstaller",
                message = "Android SDK provisioned at ${codehubSdk.absolutePath}"
            )
        )
        return allOk
    }

    private fun isSdkPackageInstalled(sdkRoot: File, packageName: String): Boolean {
        val relativePath = when {
            packageName == "platform-tools" -> "platform-tools"
            packageName.startsWith("build-tools;") -> "build-tools/${packageName.substringAfter(";")}"
            packageName.startsWith("platforms;") -> "platforms/${packageName.substringAfter(";")}"
            packageName.startsWith("ndk;") -> "ndk/${packageName.substringAfter(";")}"
            else -> return false
        }
        val dir = File(sdkRoot, relativePath)
        return dir.isDirectory && (dir.listFiles()?.isNotEmpty() == true)
    }

    private suspend fun downloadCmdlineTools(targetDir: File): Boolean {
        val url = "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
        val tempZip = File(context.cacheDir, "cmdline-tools.zip")
        val tempExtract = File(context.cacheDir, "cmdline-tools-extract")

        diagnostics.emit(
            DiagnosticEvent.now(
                kind = DiagnosticEventKind.RuntimeInitialization,
                severity = DiagnosticSeverity.Info,
                status = DiagnosticStatus.Pending,
                source = "ToolchainInstaller",
                message = "Downloading cmdline-tools from $url"
            )
        )

        val curlSpec = ProcessSpec(
            command = listOf("curl", "-L", "-o", tempZip.absolutePath, url),
            workingDirectory = "/",
            environment = emptyMap(),
            timeoutMs = 5L * 60 * 1000
        )
        val curlResult = processRunner.run(curlSpec)
        if (curlResult.exitCode != 0 || !tempZip.exists()) {
            diagnostics.emit(
                DiagnosticEvent.now(
                    kind = DiagnosticEventKind.RuntimeInitialization,
                    severity = DiagnosticSeverity.Error,
                    status = DiagnosticStatus.Failed,
                    source = "ToolchainInstaller",
                    message = "Failed to download cmdline-tools: ${curlResult.stderr.take(300)}"
                )
            )
            return false
        }

        tempExtract.deleteRecursively()
        tempExtract.mkdirs()
        val unzipSpec = ProcessSpec(
            command = listOf("unzip", "-q", tempZip.absolutePath, "-d", tempExtract.absolutePath),
            workingDirectory = "/",
            environment = emptyMap(),
            timeoutMs = 60_000
        )
        val unzipResult = processRunner.run(unzipSpec)
        if (unzipResult.exitCode != 0) {
            diagnostics.emit(
                DiagnosticEvent.now(
                    kind = DiagnosticEventKind.RuntimeInitialization,
                    severity = DiagnosticSeverity.Error,
                    status = DiagnosticStatus.Failed,
                    source = "ToolchainInstaller",
                    message = "Failed to extract cmdline-tools: ${unzipResult.stderr.take(300)}"
                )
            )
            return false
        }

        val extracted = File(tempExtract, "cmdline-tools")
        if (!extracted.isDirectory) {
            diagnostics.emit(
                DiagnosticEvent.now(
                    kind = DiagnosticEventKind.RuntimeInitialization,
                    severity = DiagnosticSeverity.Error,
                    status = DiagnosticStatus.Failed,
                    source = "ToolchainInstaller",
                    message = "cmdline-tools archive did not contain expected directory"
                )
            )
            return false
        }

        targetDir.deleteRecursively()
        targetDir.parentFile?.mkdirs()
        extracted.renameTo(targetDir)
        tempZip.delete()
        tempExtract.deleteRecursively()

        File(targetDir, "bin/sdkmanager").setExecutable(true)
        File(targetDir, "bin/avdmanager").setExecutable(true)

        return true
    }

    /**
     * Provisions missing toolchain components.
     *
     * Invariant: toolchain installation must NOT corrupt the Termux package
     * database. Specifically:
     *
     * - JDK, CMake, Ninja, Clang, Git, Adb are installed via Termux `pkg install`
     *   because they are generic Linux tools that Termux packages cleanly own.
     * - Android SDK, Platform Tools, Build Tools, Platform SDK, and NDK are
     *   installed via `sdkmanager` into CodeHub's own data dir
     *   (`context.filesDir/android-sdk/`), NOT into the Termux prefix. This
     *   isolates the SDK/NDK from Termux packages and prevents version
     *   conflicts (e.g. ndk-sysroot overwriting openssl-owned files).
     * - Gradle is NEVER installed globally. The per-project Gradle wrapper
     *   (`gradlew` + `gradle-wrapper.jar` + `gradle-wrapper.properties`) is
     *   the single source of truth for the project's Gradle version.
     */
    suspend fun ensureReady(readiness: ToolchainReadiness): ToolchainReadiness {
        if (readiness.ready) return readiness

        val missingTermux = mutableListOf<String>()
        val needSdk = mutableListOf<String>()

        readiness.missing.forEach { component ->
            when (component) {
                ToolchainComponent.Jdk -> missingTermux.add("openjdk-17")
                ToolchainComponent.Cmake -> missingTermux.add("cmake")
                ToolchainComponent.Ninja -> missingTermux.add("ninja")
                ToolchainComponent.Clang -> missingTermux.add("clang")
                ToolchainComponent.Git -> missingTermux.add("git")
                ToolchainComponent.Adb -> missingTermux.add("android-tools")
                ToolchainComponent.AndroidSdk,
                ToolchainComponent.PlatformTools,
                ToolchainComponent.BuildTools,
                ToolchainComponent.PlatformSdk,
                ToolchainComponent.Ndk -> needSdk.add(component.name)
                ToolchainComponent.Gradle -> {
                    // Gradle comes from the project wrapper; no global install needed.
                }
            }
        }

        if (missingTermux.isNotEmpty()) {
            installTermuxPackages(missingTermux)
        }
        if (needSdk.isNotEmpty()) {
            provisionAndroidSdk(needSdk)
        }

        return toolchainManager.probe()
    }

    suspend fun ensureReady(): ToolchainReadiness {
        val readiness = toolchainManager.probe()
        return ensureReady(readiness)
    }
}
