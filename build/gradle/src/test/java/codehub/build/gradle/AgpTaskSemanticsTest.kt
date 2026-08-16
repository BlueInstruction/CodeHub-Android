package codehub.build.gradle

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AgpTaskSemanticsTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `assembleDebug produces APK at expected path`() {
        val desc = AgpTaskSemantics.describe("assembleDebug")
        assertThat(desc.kind).isEqualTo(AgpTaskKind.AssembleDebug)
        assertThat(desc.producesArtifact).isTrue()
        assertThat(desc.artifactKind).isEqualTo(ArtifactKind.Apk)
        assertThat(desc.skipsSeparateInstall).isFalse()
        assertThat(desc.outputSubpath).isEqualTo("app/build/outputs/apk/debug")
    }

    @Test
    fun `installDebug skips separate install`() {
        val desc = AgpTaskSemantics.describe("installDebug")
        assertThat(desc.kind).isEqualTo(AgpTaskKind.InstallDebug)
        assertThat(desc.skipsSeparateInstall).isTrue()
        assertThat(desc.artifactKind).isEqualTo(ArtifactKind.Apk)
    }

    @Test
    fun `bundleRelease produces AAB`() {
        val desc = AgpTaskSemantics.describe("bundleRelease")
        assertThat(desc.kind).isEqualTo(AgpTaskKind.BundleRelease)
        assertThat(desc.artifactKind).isEqualTo(ArtifactKind.Aab)
        assertThat(desc.outputSubpath).isEqualTo("app/build/outputs/bundle/release")
    }

    @Test
    fun `lintDebug produces HTML report`() {
        val desc = AgpTaskSemantics.describe("lintDebug")
        assertThat(desc.kind).isEqualTo(AgpTaskKind.LintDebug)
        assertThat(desc.artifactKind).isEqualTo(ArtifactKind.LintReport)
    }

    @Test
    fun `compileDebugKotlin does not produce artifact`() {
        val desc = AgpTaskSemantics.describe("compileDebugKotlin")
        assertThat(desc.producesArtifact).isFalse()
        assertThat(desc.artifactKind).isNull()
    }

    @Test
    fun `task prefixed with app is normalized`() {
        val desc = AgpTaskSemantics.describe(":app:assembleDebug")
        assertThat(desc.kind).isEqualTo(AgpTaskKind.AssembleDebug)
    }

    @Test
    fun `unknown task is GenericGradle`() {
        val desc = AgpTaskSemantics.describe("totallyUnknownTask")
        assertThat(desc.kind).isEqualTo(AgpTaskKind.GenericGradle)
    }

    @Test
    fun `isAgpProject detects android application plugin`() {
        val projectDir = tmp.newFolder("agp-project")
        val appDir = File(projectDir, "app").apply { mkdirs() }
        File(appDir, "build.gradle.kts").writeText("""
            plugins {
                alias(libs.plugins.android.application)
                alias(libs.plugins.kotlin.android)
            }
        """.trimIndent())
        assertThat(AgpTaskSemantics.isAgpProject(projectDir.absolutePath)).isTrue()
    }

    @Test
    fun `isAgpProject detects com android application id`() {
        val projectDir = tmp.newFolder("agp-id")
        val appDir = File(projectDir, "app").apply { mkdirs() }
        File(appDir, "build.gradle.kts").writeText("""
            plugins {
                id("com.android.application")
            }
        """.trimIndent())
        assertThat(AgpTaskSemantics.isAgpProject(projectDir.absolutePath)).isTrue()
    }

    @Test
    fun `isAgpProject returns false for plain gradle`() {
        val projectDir = tmp.newFolder("plain-gradle")
        val appDir = File(projectDir, "app").apply { mkdirs() }
        File(appDir, "build.gradle.kts").writeText("""
            plugins {
                kotlin("jvm")
            }
        """.trimIndent())
        assertThat(AgpTaskSemantics.isAgpProject(projectDir.absolutePath)).isFalse()
    }

    @Test
    fun `isAgpProject returns false when app dir missing`() {
        val projectDir = tmp.newFolder("no-app")
        assertThat(AgpTaskSemantics.isAgpProject(projectDir.absolutePath)).isFalse()
    }

    @Test
    fun `primaryArtifactTask prefers APK producer`() {
        val primary = AgpTaskSemantics.primaryArtifactTask(listOf("compileDebugKotlin", "assembleDebug"))
        assertThat(primary).isNotNull()
        assertThat(primary!!.kind).isEqualTo(AgpTaskKind.AssembleDebug)
    }

    @Test
    fun `primaryArtifactTask returns null when no artifact producers`() {
        val primary = AgpTaskSemantics.primaryArtifactTask(listOf("compileDebugKotlin", "clean"))
        assertThat(primary).isNull()
    }

    @Test
    fun `describeAll handles multiple tasks`() {
        val descs = AgpTaskSemantics.describeAll(listOf("assembleDebug", "lintDebug", "clean"))
        assertThat(descs).hasSize(3)
        assertThat(descs.map { it.kind }).containsExactly(
            AgpTaskKind.AssembleDebug,
            AgpTaskKind.LintDebug,
            AgpTaskKind.Clean
        )
    }
}
