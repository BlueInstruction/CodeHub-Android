package codehub.build.gradle

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AndroidArtifactLocatorTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `findApks returns empty when no outputs exist`() {
        val projectDir = tmp.newFolder("empty")
        val apks = AndroidArtifactLocator.findApks(projectDir.absolutePath)
        assertThat(apks).isEmpty()
    }

    @Test
    fun `findApks discovers debug APK`() {
        val projectDir = tmp.newFolder("debug-apk")
        val apkDir = File(projectDir, "app/build/outputs/apk/debug").apply { mkdirs() }
        val apkFile = File(apkDir, "app-debug.apk").apply { writeBytes(ByteArray(1024)) }
        val apks = AndroidArtifactLocator.findApks(projectDir.absolutePath)
        assertThat(apks).hasSize(1)
        assertThat(apks.first().path).isEqualTo(apkFile.absolutePath)
        assertThat(apks.first().sizeBytes).isEqualTo(1024L)
    }

    @Test
    fun `findApks discovers both debug and release APKs`() {
        val projectDir = tmp.newFolder("both-apks")
        File(projectDir, "app/build/outputs/apk/debug").mkdirs()
        File(projectDir, "app/build/outputs/apk/debug/app-debug.apk").writeBytes(ByteArray(1024))
        File(projectDir, "app/build/outputs/apk/release").mkdirs()
        File(projectDir, "app/build/outputs/apk/release/app-release-unsigned.apk").writeBytes(ByteArray(2048))
        val apks = AndroidArtifactLocator.findApks(projectDir.absolutePath)
        assertThat(apks).hasSize(2)
    }

    @Test
    fun `findAabs discovers bundle outputs`() {
        val projectDir = tmp.newFolder("aabs")
        File(projectDir, "app/build/outputs/bundle/release").mkdirs()
        File(projectDir, "app/build/outputs/bundle/release/app-release.aab").writeBytes(ByteArray(4096))
        val aabs = AndroidArtifactLocator.findAabs(projectDir.absolutePath)
        assertThat(aabs).hasSize(1)
        assertThat(aabs.first().sizeBytes).isEqualTo(4096L)
    }

    @Test
    fun `findAars discovers library outputs`() {
        val projectDir = tmp.newFolder("aars")
        File(projectDir, "app/build/outputs/aar").mkdirs()
        File(projectDir, "app/build/outputs/aar/app-release.aar").writeBytes(ByteArray(2048))
        val aars = AndroidArtifactLocator.findAars(projectDir.absolutePath)
        assertThat(aars).hasSize(1)
    }

    @Test
    fun `findLintReports discovers HTML reports`() {
        val projectDir = tmp.newFolder("lint")
        File(projectDir, "app/build/reports").mkdirs()
        File(projectDir, "app/build/reports/lint-results-debug.html").writeText("<html>report</html>")
        val reports = AndroidArtifactLocator.findLintReports(projectDir.absolutePath)
        assertThat(reports).hasSize(1)
    }

    @Test
    fun `findAll aggregates all artifact kinds`() {
        val projectDir = tmp.newFolder("all")
        File(projectDir, "app/build/outputs/apk/debug").mkdirs()
        File(projectDir, "app/build/outputs/apk/debug/app-debug.apk").writeBytes(ByteArray(1))
        File(projectDir, "app/build/outputs/bundle/release").mkdirs()
        File(projectDir, "app/build/outputs/bundle/release/app.aab").writeBytes(ByteArray(1))
        File(projectDir, "app/build/outputs/aar").mkdirs()
        File(projectDir, "app/build/outputs/aar/app.aar").writeBytes(ByteArray(1))
        File(projectDir, "app/build/reports").mkdirs()
        File(projectDir, "app/build/reports/lint-results-debug.html").writeText("x")
        val all = AndroidArtifactLocator.findAll(projectDir.absolutePath)
        assertThat(all.size).isAtLeast(4)
    }

    @Test
    fun `primaryApk prefers debug over release`() {
        val projectDir = tmp.newFolder("primary")
        File(projectDir, "app/build/outputs/apk/debug").mkdirs()
        File(projectDir, "app/build/outputs/apk/debug/app-debug.apk").writeBytes(ByteArray(1))
        File(projectDir, "app/build/outputs/apk/release").mkdirs()
        File(projectDir, "app/build/outputs/apk/release/app-release.apk").writeBytes(ByteArray(1))
        val primary = AndroidArtifactLocator.primaryApk(projectDir.absolutePath)
        assertThat(primary).isNotNull()
        assertThat(primary!!.path).contains("debug")
    }

    @Test
    fun `primaryApk returns null when no APKs exist`() {
        val projectDir = tmp.newFolder("none")
        val primary = AndroidArtifactLocator.primaryApk(projectDir.absolutePath)
        assertThat(primary).isNull()
    }

    @Test
    fun `resolveArtifactName appends debug for debug APK`() {
        val name = AndroidArtifactLocator.resolveArtifactName("/tmp/app-debug.apk", "com.example.app")
        assertThat(name).isEqualTo("com.example.app (debug)")
    }

    @Test
    fun `resolveArtifactName appends release for release APK`() {
        val name = AndroidArtifactLocator.resolveArtifactName("/tmp/app-release.apk", "com.example.app")
        assertThat(name).isEqualTo("com.example.app (release)")
    }

    @Test
    fun `resolveArtifactName uses base name when no debug-release suffix`() {
        val name = AndroidArtifactLocator.resolveArtifactName("/tmp/custom.apk", "com.example.app")
        assertThat(name).isEqualTo("custom")
    }
}
