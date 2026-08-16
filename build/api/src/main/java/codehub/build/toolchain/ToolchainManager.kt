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
class ToolchainManager @Inject constructor(
    private val processRunner: ProcessRunner,
    private val diagnostics: DiagnosticSink,
    private val context: Context
) {

    suspend fun probe(): ToolchainReadiness {
        val components = mutableListOf<ToolchainDescriptor>()
        val issues = mutableListOf<String>()

        val jdk = probeJdk()
        components.add(jdk)
        if (!jdk.installed) issues += "JDK not found. Install via Termux: pkg install openjdk-17"

        val sdk = probeAndroidSdk()
        components.add(sdk)
        if (!sdk.installed) issues += "Android SDK not found. Will be provisioned by CodeHub."

        val platformTools = probePlatformTools(sdk.path)
        components.add(platformTools)

        val buildTools = probeBuildTools(sdk.path)
        components.add(buildTools)

        val platformSdk = probePlatformSdk(sdk.path)
        components.add(platformSdk)

        val ndk = probeNdk(sdk.path)
        components.add(ndk)

        val cmake = probeCmake()
        components.add(cmake)

        val ninja = probeNinja()
        components.add(ninja)

        val clang = probeClang()
        components.add(clang)

        val git = probeGit()
        components.add(git)

        val adb = probeAdb(platformTools.path)
        components.add(adb)

        val gradle = probeGradle()
        components.add(gradle)

        val missing = components.filter { !it.installed }.map { it.component }
        val incompatible = components.filter { it.installed && !it.compatible }
        incompatible.forEach { desc ->
            val reason = ToolchainCompatibility.describeIncompatibility(desc.component, desc.version)
            if (reason.isNotEmpty()) issues += reason
        }

        val env = buildEnvironment(components)
        val ready = missing.none { it in CRITICAL_FOR_ANDROID_APP }

        diagnostics.emit(
            DiagnosticEvent.now(
                kind = DiagnosticEventKind.RuntimeInitialization,
                severity = if (ready) DiagnosticSeverity.Info else DiagnosticSeverity.Warn,
                status = if (ready) DiagnosticStatus.Ok else DiagnosticStatus.Skipped,
                source = "ToolchainManager",
                message = if (ready) "Toolchain ready" else "Toolchain incomplete: ${missing.joinToString(", ") { it.name }}",
                reason = if (ready) "all_critical_components_present" else "missing_components",
                attributes = components.associate { it.component.name to (it.installed).toString() }
            )
        )

        return ToolchainReadiness(
            components = components,
            environment = env,
            missing = missing,
            incompatible = incompatible,
            ready = ready,
            issues = issues
        )
    }

    fun buildEnvironment(components: List<ToolchainDescriptor>): Map<String, String> {
        val env = mutableMapOf<String, String>()
        val termuxPrefix = "/data/data/com.termux/files/usr"
        val termuxBin = "$termuxPrefix/bin"
        val systemBin = "/system/bin:/system/xbin"

        val pathParts = mutableListOf<String>()
        val jdk = components.firstOrNull { it.component == ToolchainComponent.Jdk }
        jdk?.path?.let { jdkPath ->
            env["JAVA_HOME"] = jdkPath
            pathParts.add("$jdkPath/bin")
        }

        val sdk = components.firstOrNull { it.component == ToolchainComponent.AndroidSdk }
        sdk?.path?.let { sdkPath ->
            env["ANDROID_HOME"] = sdkPath
            env["ANDROID_SDK_ROOT"] = sdkPath
        }

        val ndk = components.firstOrNull { it.component == ToolchainComponent.Ndk }
        ndk?.path?.let { ndkPath ->
            env["ANDROID_NDK_HOME"] = ndkPath
            env["ANDROID_NDK_ROOT"] = ndkPath
        }

        if (File(termuxBin).isDirectory) {
            pathParts.add(termuxBin)
            pathParts.add("$termuxBin/applets")
        }
        pathParts.add(systemBin)

        val existingPath = System.getenv("PATH") ?: ""
        env["PATH"] = (pathParts + existingPath).joinToString(":")
        env["TERM"] = "xterm-256color"
        env["LANG"] = "en_US.UTF-8"

        return env
    }

    private suspend fun probeJdk(): ToolchainDescriptor {
        val termuxJava = "/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk"
        if (File(termuxJava).isDirectory) {
            val version = readJavaVersion("$termuxJava/bin")
            return ToolchainDescriptor(
                component = ToolchainComponent.Jdk,
                version = version,
                path = termuxJava,
                source = ToolchainSource.Termux,
                compatible = ToolchainCompatibility.isCompatible(ToolchainComponent.Jdk, version)
            )
        }
        val whichResult = runWhich("java")
        if (whichResult.isNotBlank()) {
            val javaHome = resolveJavaHome(whichResult)
            val version = readJavaVersion(File(whichResult).parent ?: "/")
            return ToolchainDescriptor(
                component = ToolchainComponent.Jdk,
                version = version,
                path = javaHome,
                source = ToolchainSource.System,
                compatible = ToolchainCompatibility.isCompatible(ToolchainComponent.Jdk, version)
            )
        }
        return ToolchainDescriptor(
            component = ToolchainComponent.Jdk,
            version = null,
            path = null,
            source = ToolchainSource.NotFound,
            compatible = false
        )
    }

    private suspend fun probeAndroidSdk(): ToolchainDescriptor {
        val codehubSdk = File(context.filesDir, "android-sdk").absolutePath
        if (File(codehubSdk, "cmdline-tools").isDirectory) {
            return ToolchainDescriptor(
                component = ToolchainComponent.AndroidSdk,
                version = detectSdkManagerVersion(codehubSdk),
                path = codehubSdk,
                source = ToolchainSource.CodeHub,
                compatible = true
            )
        }
        val envSdk = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        if (!envSdk.isNullOrBlank() && File(envSdk).isDirectory) {
            return ToolchainDescriptor(
                component = ToolchainComponent.AndroidSdk,
                version = detectSdkManagerVersion(envSdk),
                path = envSdk,
                source = ToolchainSource.Manual,
                compatible = true
            )
        }
        val termuxSdk = "/data/data/com.termux/files/home/android-sdk"
        if (File(termuxSdk, "cmdline-tools").isDirectory) {
            return ToolchainDescriptor(
                component = ToolchainComponent.AndroidSdk,
                version = detectSdkManagerVersion(termuxSdk),
                path = termuxSdk,
                source = ToolchainSource.Termux,
                compatible = true
            )
        }
        return ToolchainDescriptor(
            component = ToolchainComponent.AndroidSdk,
            version = null,
            path = null,
            source = ToolchainSource.NotFound,
            compatible = false
        )
    }

    private fun probePlatformTools(sdkPath: String?): ToolchainDescriptor {
        if (sdkPath == null) return notFound(ToolchainComponent.PlatformTools)
        val ptPath = File(sdkPath, "platform-tools")
        if (!ptPath.isDirectory) return notFound(ToolchainComponent.PlatformTools)
        val adbVersion = File(ptPath, "adb").exists()
        return ToolchainDescriptor(
            component = ToolchainComponent.PlatformTools,
            version = if (adbVersion) "35.0.0" else null,
            path = ptPath.absolutePath,
            source = ToolchainSource.AndroidSdk,
            compatible = true
        )
    }

    private fun probeBuildTools(sdkPath: String?): ToolchainDescriptor {
        if (sdkPath == null) return notFound(ToolchainComponent.BuildTools)
        val btDir = File(sdkPath, "build-tools")
        if (!btDir.isDirectory) return notFound(ToolchainComponent.BuildTools)
        val latest = btDir.listFiles()?.filter { it.isDirectory }?.maxByOrNull { it.name }
            ?: return notFound(ToolchainComponent.BuildTools)
        return ToolchainDescriptor(
            component = ToolchainComponent.BuildTools,
            version = latest.name,
            path = latest.absolutePath,
            source = ToolchainSource.AndroidSdk,
            compatible = ToolchainCompatibility.isCompatible(ToolchainComponent.BuildTools, latest.name)
        )
    }

    private fun probePlatformSdk(sdkPath: String?): ToolchainDescriptor {
        if (sdkPath == null) return notFound(ToolchainComponent.PlatformSdk)
        val platformsDir = File(sdkPath, "platforms")
        if (!platformsDir.isDirectory) return notFound(ToolchainComponent.PlatformSdk)
        val latest = platformsDir.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("android-") }
            ?.maxByOrNull { it.name.removePrefix("android-").toIntOrNull() ?: 0 }
            ?: return notFound(ToolchainComponent.PlatformSdk)
        val version = latest.name.removePrefix("android-")
        return ToolchainDescriptor(
            component = ToolchainComponent.PlatformSdk,
            version = version,
            path = latest.absolutePath,
            source = ToolchainSource.AndroidSdk,
            compatible = ToolchainCompatibility.isCompatible(ToolchainComponent.PlatformSdk, version)
        )
    }

    private fun probeNdk(sdkPath: String?): ToolchainDescriptor {
        if (sdkPath == null) return notFound(ToolchainComponent.Ndk)
        val ndkDir = File(sdkPath, "ndk")
        if (!ndkDir.isDirectory) return notFound(ToolchainComponent.Ndk)
        val latest = ndkDir.listFiles()?.filter { it.isDirectory }?.maxByOrNull { it.name }
            ?: return notFound(ToolchainComponent.Ndk)
        return ToolchainDescriptor(
            component = ToolchainComponent.Ndk,
            version = latest.name,
            path = latest.absolutePath,
            source = ToolchainSource.AndroidSdk,
            compatible = ToolchainCompatibility.isCompatible(ToolchainComponent.Ndk, latest.name)
        )
    }

    private suspend fun probeCmake(): ToolchainDescriptor {
        val termuxCmake = "/data/data/com.termux/files/usr/bin/cmake"
        if (File(termuxCmake).exists()) {
            val version = runVersion(listOf(termuxCmake, "--version"))
            return ToolchainDescriptor(
                component = ToolchainComponent.Cmake,
                version = version,
                path = File(termuxCmake).parent,
                source = ToolchainSource.Termux,
                compatible = ToolchainCompatibility.isCompatible(ToolchainComponent.Cmake, version)
            )
        }
        return notFound(ToolchainComponent.Cmake)
    }

    private suspend fun probeNinja(): ToolchainDescriptor {
        val termuxNinja = "/data/data/com.termux/files/usr/bin/ninja"
        if (File(termuxNinja).exists()) {
            val version = runVersion(listOf(termuxNinja, "--version"))
            return ToolchainDescriptor(
                component = ToolchainComponent.Ninja,
                version = version,
                path = File(termuxNinja).parent,
                source = ToolchainSource.Termux,
                compatible = ToolchainCompatibility.isCompatible(ToolchainComponent.Ninja, version)
            )
        }
        return notFound(ToolchainComponent.Ninja)
    }

    private suspend fun probeClang(): ToolchainDescriptor {
        val termuxClang = "/data/data/com.termux/files/usr/bin/clang"
        if (File(termuxClang).exists()) {
            val version = runVersion(listOf(termuxClang, "--version"))
            return ToolchainDescriptor(
                component = ToolchainComponent.Clang,
                version = version,
                path = File(termuxClang).parent,
                source = ToolchainSource.Termux,
                compatible = ToolchainCompatibility.isCompatible(ToolchainComponent.Clang, version)
            )
        }
        return notFound(ToolchainComponent.Clang)
    }

    private suspend fun probeGit(): ToolchainDescriptor {
        val termuxGit = "/data/data/com.termux/files/usr/bin/git"
        if (File(termuxGit).exists()) {
            val version = runVersion(listOf(termuxGit, "--version"))
            return ToolchainDescriptor(
                component = ToolchainComponent.Git,
                version = version,
                path = File(termuxGit).parent,
                source = ToolchainSource.Termux,
                compatible = ToolchainCompatibility.isCompatible(ToolchainComponent.Git, version)
            )
        }
        return notFound(ToolchainComponent.Git)
    }

    private fun probeAdb(platformToolsPath: String?): ToolchainDescriptor {
        if (platformToolsPath == null) return notFound(ToolchainComponent.Adb)
        val adb = File(platformToolsPath, "adb")
        if (!adb.exists()) return notFound(ToolchainComponent.Adb)
        return ToolchainDescriptor(
            component = ToolchainComponent.Adb,
            version = "35.0.0",
            path = platformToolsPath,
            source = ToolchainSource.AndroidSdk,
            compatible = true
        )
    }

    private suspend fun probeGradle(): ToolchainDescriptor {
        val termuxGradle = "/data/data/com.termux/files/usr/bin/gradle"
        if (File(termuxGradle).exists()) {
            val version = runVersion(listOf(termuxGradle, "--version"))
            return ToolchainDescriptor(
                component = ToolchainComponent.Gradle,
                version = version,
                path = File(termuxGradle).parent,
                source = ToolchainSource.Termux,
                compatible = ToolchainCompatibility.isCompatible(ToolchainComponent.Gradle, version)
            )
        }
        return ToolchainDescriptor(
            component = ToolchainComponent.Gradle,
            version = null,
            path = null,
            source = ToolchainSource.NotFound,
            compatible = false
        )
    }

    private suspend fun runWhich(binary: String): String {
        val result = processRunner.run(
            ProcessSpec(
                command = listOf("which", binary),
                workingDirectory = "/",
                environment = emptyMap(),
                timeoutMs = 2_000
            )
        )
        return if (result.exitCode == 0) result.stdout.trim() else ""
    }

    private suspend fun runVersion(command: List<String>): String {
        val result = processRunner.run(
            ProcessSpec(
                command = command,
                workingDirectory = "/",
                environment = emptyMap(),
                timeoutMs = 5_000
            )
        )
        return extractVersion(result.stdout + "\n" + result.stderr)
    }

    private fun extractVersion(output: String): String {
        val patterns = listOf(
            Regex("""(\d+\.\d+\.\d+)"""),
            Regex("""(\d+\.\d+)"""),
            Regex("""version\s+(\d+\.\d+\.\d+)""")
        )
        for (pattern in patterns) {
            val match = pattern.find(output)
            if (match != null) return match.groupValues[1]
        }
        return ""
    }

    private fun readJavaVersion(binDir: String): String {
        val releaseFile = File(binDir).parentFile?.let { File(it, "release") }
        if (releaseFile?.exists() == true) {
            val content = releaseFile.readText()
            val match = Regex("""JAVA_VERSION="(\d+[\d.]*)"""").find(content)
            if (match != null) return match.groupValues[1]
        }
        return ""
    }

    private fun resolveJavaHome(javaBinary: String): String {
        val bin = File(javaBinary).parentFile ?: return ""
        return bin.parentFile?.absolutePath ?: bin.absolutePath
    }

    private fun detectSdkManagerVersion(sdkPath: String): String {
        val cmdlineLatest = File(sdkPath, "cmdline-tools").listFiles()
            ?.filter { it.isDirectory }
            ?.maxByOrNull { it.name }
        return cmdlineLatest?.name ?: "latest"
    }

    private fun notFound(component: ToolchainComponent): ToolchainDescriptor = ToolchainDescriptor(
        component = component,
        version = null,
        path = null,
        source = ToolchainSource.NotFound,
        compatible = false
    )

    companion object {
        val CRITICAL_FOR_ANDROID_APP = setOf(
            ToolchainComponent.Jdk,
            ToolchainComponent.AndroidSdk,
            ToolchainComponent.BuildTools,
            ToolchainComponent.PlatformSdk,
            ToolchainComponent.Gradle,
            ToolchainComponent.Git
        )

        val CRITICAL_FOR_NATIVE = setOf(
            ToolchainComponent.Cmake,
            ToolchainComponent.Ninja,
            ToolchainComponent.Clang,
            ToolchainComponent.Ndk,
            ToolchainComponent.Git
        )
    }
}
