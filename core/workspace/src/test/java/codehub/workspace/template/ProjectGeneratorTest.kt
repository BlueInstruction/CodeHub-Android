package codehub.workspace.template

import com.google.common.truth.Truth.assertThat
import codehub.core.diagnostics.InMemoryDiagnosticSink
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProjectGeneratorTest {

    @get:Rule val tmp = TemporaryFolder()

    private val sink = InMemoryDiagnosticSink()
    private val wrapperAssets = TestWrapperAssets()
    private val generator = ProjectGenerator(sink, wrapperAssets)

    @Test
    fun `EmptyCompose template generates full project tree`() = runTest {
        val projectDir = tmp.newFolder("compose-app")
        val template = ProjectTemplateRegistry.get(ProjectTemplateKind.EmptyCompose)
        val request = ProjectGenerationRequest(
            template = template,
            projectPath = projectDir.absolutePath,
            packageName = "com.example.myapp",
            displayName = "My App"
        )
        val result = generator.generate(request)
        assertThat(result.success).isTrue()
        assertThat(result.applicationId).isEqualTo("com.example.myapp")
        assertThat(result.mainActivityClass).isEqualTo("com.example.myapp.MainActivity")
        assertThat(result.filesWritten.size).isAtLeast(15)
    }

    @Test
    fun `EmptyCompose writes settings gradle with project name`() = runTest {
        val projectDir = tmp.newFolder("compose-named")
        val template = ProjectTemplateRegistry.get(ProjectTemplateKind.EmptyCompose)
        generator.generate(
            ProjectGenerationRequest(
                template = template,
                projectPath = projectDir.absolutePath,
                packageName = "io.codehub.sample",
                displayName = "Sample"
            )
        )
        val settings = File(projectDir, "settings.gradle.kts").readText()
        assertThat(settings).contains("rootProject.name = \"Sample\"")
        assertThat(settings).contains("include(\":app\")")
    }

    @Test
    fun `EmptyCompose writes app build gradle with applicationId`() = runTest {
        val projectDir = tmp.newFolder("compose-appid")
        val template = ProjectTemplateRegistry.get(ProjectTemplateKind.EmptyCompose)
        generator.generate(
            ProjectGenerationRequest(
                template = template,
                projectPath = projectDir.absolutePath,
                packageName = "com.test.app",
                displayName = "Test"
            )
        )
        val appGradle = File(projectDir, "app/build.gradle.kts").readText()
        assertThat(appGradle).contains("applicationId = \"com.test.app\"")
        assertThat(appGradle).contains("namespace = \"com.test.app\"")
        assertThat(appGradle).contains("minSdk = 24")
        assertThat(appGradle).contains("targetSdk = 35")
        assertThat(appGradle).contains("compose = true")
    }

    @Test
    fun `EmptyCompose writes MainActivity with Compose content`() = runTest {
        val projectDir = tmp.newFolder("compose-activity")
        val template = ProjectTemplateRegistry.get(ProjectTemplateKind.EmptyCompose)
        generator.generate(
            ProjectGenerationRequest(
                template = template,
                projectPath = projectDir.absolutePath,
                packageName = "com.test.app",
                displayName = "Test"
            )
        )
        val mainActivity = File(projectDir, "app/src/main/java/com/test/app/MainActivity.kt").readText()
        assertThat(mainActivity).contains("package com.test.app")
        assertThat(mainActivity).contains("class MainActivity : ComponentActivity()")
        assertThat(mainActivity).contains("setContent")
        assertThat(mainActivity).contains("Greeting")
    }

    @Test
    fun `EmptyCompose writes version catalog with AGP and Kotlin versions`() = runTest {
        val projectDir = tmp.newFolder("compose-versions")
        val template = ProjectTemplateRegistry.get(ProjectTemplateKind.EmptyCompose)
        generator.generate(
            ProjectGenerationRequest(
                template = template,
                projectPath = projectDir.absolutePath,
                packageName = "com.test.app",
                displayName = "Test"
            )
        )
        val catalog = File(projectDir, "gradle/libs.versions.toml").readText()
        assertThat(catalog).contains("agp = \"8.7.3\"")
        assertThat(catalog).contains("kotlin = \"2.0.21\"")
        assertThat(catalog).contains("composeBom")
    }

    @Test
    fun `EmptyCompose writes gradle wrapper with correct version`() = runTest {
        val projectDir = tmp.newFolder("compose-wrapper")
        val template = ProjectTemplateRegistry.get(ProjectTemplateKind.EmptyCompose)
        generator.generate(
            ProjectGenerationRequest(
                template = template,
                projectPath = projectDir.absolutePath,
                packageName = "com.test.app",
                displayName = "Test"
            )
        )
        val wrapper = File(projectDir, "gradle/wrapper/gradle-wrapper.properties").readText()
        assertThat(wrapper).contains("gradle-8.10.2-bin.zip")
    }

    @Test
    fun `EmptyCompose writes AndroidManifest with launcher activity`() = runTest {
        val projectDir = tmp.newFolder("compose-manifest")
        val template = ProjectTemplateRegistry.get(ProjectTemplateKind.EmptyCompose)
        generator.generate(
            ProjectGenerationRequest(
                template = template,
                projectPath = projectDir.absolutePath,
                packageName = "com.test.app",
                displayName = "Test App"
            )
        )
        val manifest = File(projectDir, "app/src/main/AndroidManifest.xml").readText()
        assertThat(manifest).contains("android:name=\".MainActivity\"")
        assertThat(manifest).contains("android.intent.category.LAUNCHER")
        assertThat(manifest).contains("android:label=\"Test App\"")
    }

    @Test
    fun `NativeActivity template writes CMake and native source`() = runTest {
        val projectDir = tmp.newFolder("native-app")
        val template = ProjectTemplateRegistry.get(ProjectTemplateKind.NativeActivity)
        val result = generator.generate(
            ProjectGenerationRequest(
                template = template,
                projectPath = projectDir.absolutePath,
                packageName = "com.test.native",
                displayName = "Native"
            )
        )
        assertThat(result.success).isTrue()
        val cmakeLists = File(projectDir, "app/src/main/cpp/CMakeLists.txt")
        assertThat(cmakeLists.exists()).isTrue()
        val nativeSrc = File(projectDir, "app/src/main/cpp/native-lib.cpp")
        assertThat(nativeSrc.exists()).isTrue()
        val nativeContent = nativeSrc.readText()
        assertThat(nativeContent).contains("Java_com_test_native_MainActivity_stringFromJNI")
        val appGradle = File(projectDir, "app/build.gradle.kts").readText()
        assertThat(appGradle).contains("externalNativeBuild")
        assertThat(appGradle).contains("cmake")
        assertThat(appGradle).contains("arm64-v8a")
    }

    @Test
    fun `AndroidLibrary template does not write applicationId`() = runTest {
        val projectDir = tmp.newFolder("lib-app")
        val template = ProjectTemplateRegistry.get(ProjectTemplateKind.AndroidLibrary)
        generator.generate(
            ProjectGenerationRequest(
                template = template,
                projectPath = projectDir.absolutePath,
                packageName = "com.test.lib",
                displayName = "Lib"
            )
        )
        val appGradle = File(projectDir, "app/build.gradle.kts").readText()
        assertThat(appGradle).doesNotContain("applicationId")
        assertThat(appGradle).contains("android.library")
    }

    @Test
    fun `non-empty directory fails generation`() = runTest {
        val projectDir = tmp.newFolder("occupied")
        File(projectDir, "existing.txt").writeText("x")
        val template = ProjectTemplateRegistry.get(ProjectTemplateKind.EmptyCompose)
        val result = generator.generate(
            ProjectGenerationRequest(
                template = template,
                projectPath = projectDir.absolutePath,
                packageName = "com.test.app",
                displayName = "Test"
            )
        )
        assertThat(result.success).isFalse()
        assertThat(result.errors.first()).contains("not empty")
    }

    @Test
    fun `invalid package name fails validation`() {
        val template = ProjectTemplateRegistry.get(ProjectTemplateKind.EmptyCompose)
        try {
            ProjectGenerationRequest(
                template = template,
                projectPath = "/tmp/x",
                packageName = "Invalid.Package.Name",
                displayName = "Test"
            )
            assert(false) { "Should have thrown for invalid package name" }
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("Package name")
        }
    }

    @Test
    fun `blank display name fails validation`() {
        val template = ProjectTemplateRegistry.get(ProjectTemplateKind.EmptyCompose)
        try {
            ProjectGenerationRequest(
                template = template,
                projectPath = "/tmp/x",
                packageName = "com.test.app",
                displayName = ""
            )
            assert(false) { "Should have thrown for blank display name" }
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("Display name")
        }
    }

    @Test
    fun `minSdk above targetSdk fails validation`() {
        val template = ProjectTemplateRegistry.get(ProjectTemplateKind.EmptyCompose)
        try {
            ProjectGenerationRequest(
                template = template,
                projectPath = "/tmp/x",
                packageName = "com.test.app",
                displayName = "Test",
                minSdk = 30,
                targetSdk = 24
            )
            assert(false) { "Should have thrown for minSdk > targetSdk" }
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("minSdk")
        }
    }

    @Test
    fun `generated gradlew is executable`() = runTest {
        val projectDir = tmp.newFolder("exec-test")
        val template = ProjectTemplateRegistry.get(ProjectTemplateKind.EmptyCompose)
        generator.generate(
            ProjectGenerationRequest(
                template = template,
                projectPath = projectDir.absolutePath,
                packageName = "com.test.app",
                displayName = "Test"
            )
        )
        val gradlew = File(projectDir, "gradlew")
        assertThat(gradlew.exists()).isTrue()
        assertThat(gradlew.canExecute()).isTrue()
    }

    @Test
    fun `README contains template and package info`() = runTest {
        val projectDir = tmp.newFolder("readme-test")
        val template = ProjectTemplateRegistry.get(ProjectTemplateKind.EmptyCompose)
        generator.generate(
            ProjectGenerationRequest(
                template = template,
                projectPath = projectDir.absolutePath,
                packageName = "com.test.app",
                displayName = "Test App"
            )
        )
        val readme = File(projectDir, "README.md").readText()
        assertThat(readme).contains("Test App")
        assertThat(readme).contains("com.test.app")
        assertThat(readme).contains("Empty Compose Activity")
        assertThat(readme).contains("assembleDebug")
    }

    @Test
    fun `gradle-wrapper jar is binary and non-trivial`() = runTest {
        val projectDir = tmp.newFolder("wrapper-jar")
        val template = ProjectTemplateRegistry.get(ProjectTemplateKind.EmptyCompose)
        generator.generate(
            ProjectGenerationRequest(
                template = template,
                projectPath = projectDir.absolutePath,
                packageName = "com.test.app",
                displayName = "Test"
            )
        )
        val jar = File(projectDir, "gradle/wrapper/gradle-wrapper.jar")
        assertThat(jar.exists()).isTrue()
        assertThat(jar.length()).isGreaterThan(1000L)
        val header = jar.readBytes().copyOfRange(0, 4)
        assertThat(header[0]).isEqualTo(0x50.toByte()) // 'P'
        assertThat(header[1]).isEqualTo(0x4b.toByte()) // 'K'
        assertThat(header[2]).isEqualTo(0x03.toByte()) // 0x03
        assertThat(header[3]).isEqualTo(0x04.toByte()) // 0x04
    }

    @Test
    fun `gradlew is the real Gradle wrapper script`() = runTest {
        val projectDir = tmp.newFolder("real-gradlew")
        val template = ProjectTemplateRegistry.get(ProjectTemplateKind.EmptyCompose)
        generator.generate(
            ProjectGenerationRequest(
                template = template,
                projectPath = projectDir.absolutePath,
                packageName = "com.test.app",
                displayName = "Test"
            )
        )
        val gradlew = File(projectDir, "gradlew")
        assertThat(gradlew.exists()).isTrue()
        val content = gradlew.readText()
        assertThat(content).contains("GradleWrapperMain")
        assertThat(content).contains("APP_HOME")
        assertThat(content.length).isGreaterThan(1000)
        assertThat(gradlew.canExecute()).isTrue()
    }

    @Test
    fun `gradle-wrapper version is compatible with AGP version for all templates`() {
        ProjectTemplateRegistry.templates.forEach { template ->
            val (requiredGradle, _, _) = codehub.build.toolchain.ToolchainCompatibility
                .agpGradleJdkMatrix(template.agpVersion)
            val compatible = codehub.build.toolchain.ToolchainCompatibility
                .isCompatible(codehub.build.toolchain.ToolchainComponent.Gradle, template.gradleVersion)
            if (!compatible) {
                throw AssertionError("Template ${template.kind} has Gradle ${template.gradleVersion} but AGP ${template.agpVersion} requires Gradle $requiredGradle+")
            }
        }
    }

    @Test
    fun `gradle-wrapper properties contains the template Gradle version`() = runTest {
        val projectDir = tmp.newFolder("wrapper-version")
        val template = ProjectTemplateRegistry.get(ProjectTemplateKind.EmptyCompose)
        generator.generate(
            ProjectGenerationRequest(
                template = template,
                projectPath = projectDir.absolutePath,
                packageName = "com.test.app",
                displayName = "Test"
            )
        )
        val props = File(projectDir, "gradle/wrapper/gradle-wrapper.properties").readText()
        assertThat(props).contains("gradle-${template.gradleVersion}-bin.zip")
    }
}

private class TestWrapperAssets : WrapperAssets {
    override fun gradlewScript(): ByteArray = REAL_GRADLEW.toByteArray()
    override fun gradlewBatScript(): ByteArray = REAL_GRADLEW_BAT.toByteArray()
    override fun gradleWrapperJar(): ByteArray = FAKE_JAR_BYTES

    private val REAL_GRADLEW = buildString {
        appendLine("#!/bin/sh")
        appendLine("# Gradle wrapper script")
        appendLine("APP_HOME=\\$PWD")
        appendLine("CLASSPATH=\\$APP_HOME/gradle/wrapper/gradle-wrapper.jar")
        appendLine("exec java -classpath \"\\$CLASSPATH\" org.gradle.wrapper.GradleWrapperMain \"\\$@\"")
        repeat(50) { appendLine("# padding line $it to exceed 1000 chars for the test") }
    }
    private val REAL_GRADLEW_BAT = "@rem Gradle startup script for Windows\r\n"
    private val FAKE_JAR_BYTES = ByteArray(2000) { 0 }
}
