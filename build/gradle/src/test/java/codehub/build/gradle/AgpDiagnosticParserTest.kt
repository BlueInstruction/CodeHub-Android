package codehub.build.gradle

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AgpDiagnosticParserTest {

    @Test
    fun `parses Kotlin compiler error`() {
        val stdout = """
            e: file:///app/src/main/java/com/example/MainActivity.kt:10:5: Unresolved reference: foo
        """.trimIndent()
        val diagnostics = AgpDiagnosticParser.parse(stdout, "")
        val errors = diagnostics.filter { it.severity == "error" }
        assertThat(errors).isNotEmpty()
        assertThat(errors.first().message).contains("Unresolved reference: foo")
        assertThat(errors.first().line).isEqualTo(10)
        assertThat(errors.first().tool).isEqualTo("kotlin")
    }

    @Test
    fun `parses Kotlin warning`() {
        val stdout = """
            w: file:///app/src/main/java/com/example/Foo.kt:5:1: Parameter 'unused' is never used
        """.trimIndent()
        val diagnostics = AgpDiagnosticParser.parse(stdout, "")
        val warnings = diagnostics.filter { it.severity == "warning" }
        assertThat(warnings).isNotEmpty()
        assertThat(warnings.first().message).contains("Parameter 'unused'")
    }

    @Test
    fun `parses AGPBI error format`() {
        val stdout = """
            AGPBI: {"kind":"error","text":"Manifest merger failed","sources":[{"file":"/app/src/main/AndroidManifest.xml","position":{"startLine":15}}]}
        """.trimIndent()
        val diagnostics = AgpDiagnosticParser.parse(stdout, "")
        val agpErrors = diagnostics.filter { it.tool == "agp" }
        assertThat(agpErrors).isNotEmpty()
        assertThat(agpErrors.first().message).contains("Manifest merger failed")
    }

    @Test
    fun `parses execution failed message`() {
        val stdout = """
            > Task :app:compileDebugKotlin FAILED

            FAILURE: Build failed with an exception.

            * What went wrong:
            Execution failed for task ':app:compileDebugKotlin'.
        """.trimIndent()
        val diagnostics = AgpDiagnosticParser.parse(stdout, "")
        val gradleErrors = diagnostics.filter { it.tool == "gradle" }
        assertThat(gradleErrors).isNotEmpty()
        assertThat(gradleErrors.first().message).contains("Execution failed for task ':app:compileDebugKotlin'")
    }

    @Test
    fun `parses what went wrong block when no specific error captured`() {
        val stdout = """
            FAILURE: Build failed with an exception.

            * What went wrong:
            A problem occurred configuring project ':app'.
            > Could not resolve all dependencies for configuration ':app:debugRuntimeClasspath'.
        """.trimIndent()
        val diagnostics = AgpDiagnosticParser.parse(stdout, "")
        val gradleErrors = diagnostics.filter { it.tool == "gradle" }
        assertThat(gradleErrors).isNotEmpty()
        assertThat(gradleErrors.first().message).contains("A problem occurred")
    }

    @Test
    fun `deduplicates identical diagnostics`() {
        val stdout = """
            e: file:///app/src/main/java/com/example/Foo.kt:5:1: Unresolved reference: bar
            e: file:///app/src/main/java/com/example/Foo.kt:5:1: Unresolved reference: bar
        """.trimIndent()
        val diagnostics = AgpDiagnosticParser.parse(stdout, "")
        val uniqueErrors = diagnostics.filter { it.severity == "error" }
        assertThat(uniqueErrors).hasSize(1)
    }

    @Test
    fun `returns empty list for empty output`() {
        val diagnostics = AgpDiagnosticParser.parse("", "")
        assertThat(diagnostics).isEmpty()
    }

    @Test
    fun `parses multiple errors from same file`() {
        val stdout = """
            e: file:///app/src/main/java/com/example/Foo.kt:5:1: Unresolved reference: bar
            e: file:///app/src/main/java/com/example/Foo.kt:10:1: Unresolved reference: baz
        """.trimIndent()
        val diagnostics = AgpDiagnosticParser.parse(stdout, "")
        val errors = diagnostics.filter { it.severity == "error" }
        assertThat(errors).hasSize(2)
        assertThat(errors.map { it.line }).containsExactly(5, 10)
    }

    @Test
    fun `stderr is included in parsing`() {
        val stderr = """
            e: file:///app/src/main/java/com/example/Bar.kt:3:5: Type mismatch
        """.trimIndent()
        val diagnostics = AgpDiagnosticParser.parse("", stderr)
        assertThat(diagnostics.filter { it.severity == "error" }).isNotEmpty()
    }
}
