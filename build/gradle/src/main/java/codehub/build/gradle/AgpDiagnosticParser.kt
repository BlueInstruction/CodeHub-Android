package codehub.build.gradle

import codehub.build.api.BuildDiagnostic

object AgpDiagnosticParser {

    private val kotlinPattern = Regex(
        """(?:e:|w:)\s+(?:file:)?(?<file>[^\s:]+):(?<line>\d+):(?<col>\d+):\s+(?<severity>error|warning):\s*(?<message>.+)"""
    )

    private val agpFailurePattern = Regex(
        """AGPBI:\s*\{.*?"kind":"(?<kind>error|warning|info|fatal)".*?"text":"(?<text>[^"]+)".*?"file":\s*\[(?:"(?<file>[^"]+)")?.*?"line":\s*(?<line>\d+)?[^}]*\}"""
    )

    private val manifestMergePattern = Regex(
        """(?<severity>ERROR|WARNING):\s+(?<file>[^\s:]+):(?<line>\d+):?\s*(?<message>.+)"""
    )

    private val taskFailedPattern = Regex(
        """FAILURE:\s*Build (?:failed|completed) with an exception\.\n.*?\n.*?\n(?<file>[^\s:]+):(?<line>\d+):(?: error:)?\s*(?<message>.+)""",
        RegexOption.DOT_MATCHES_ALL
    )

    private val executionFailedPattern = Regex(
        """Execution failed for task '(?<task>[^']+)'\."""
    )

    private val lintErrorPattern = Regex(
        """^\s*(?<severity>Error|Warning|Fatal):\s+(?<message>.+)(?:\s+:\s+(?<file>[^\s:]+):(?<line>\d+))?""",
        RegexOption.MULTILINE
    )

    private val whatWentWrongPattern = Regex(
        """\* What went wrong:\n(?<message>(?:.*\n){1,10})""",
        RegexOption.MULTILINE
    )

    fun parse(stdout: String, stderr: String): List<BuildDiagnostic> {
        val combined = "$stdout\n$stderr"
        val results = mutableListOf<BuildDiagnostic>()

        for (match in kotlinPattern.findAll(combined)) {
            val groups = match.groups
            val severity = groups["severity"]?.value?.lowercase() ?: "info"
            val file = groups["file"]?.value
            val line = groups["line"]?.value?.toIntOrNull()
            val col = groups["col"]?.value?.toIntOrNull()
            val message = groups["message"]?.value?.trim() ?: ""
            if (message.isNotBlank()) {
                results.add(
                    BuildDiagnostic(
                        severity = severity,
                        file = file,
                        line = line,
                        column = col,
                        code = null,
                        message = message,
                        tool = "kotlin"
                    )
                )
            }
        }

        for (match in agpFailurePattern.findAll(combined)) {
            val groups = match.groups
            val severity = groups["kind"]?.value?.lowercase() ?: "info"
            val text = groups["text"]?.value?.trim() ?: ""
            val file = groups["file"]?.value
            val line = groups["line"]?.value?.toIntOrNull()
            if (text.isNotBlank()) {
                results.add(
                    BuildDiagnostic(
                        severity = severity,
                        file = file,
                        line = line,
                        column = null,
                        code = null,
                        message = text,
                        tool = "agp"
                    )
                )
            }
        }

        for (match in manifestMergePattern.findAll(combined)) {
            val groups = match.groups
            val severity = groups["severity"]?.value?.lowercase() ?: "info"
            val file = groups["file"]?.value
            val line = groups["line"]?.value?.toIntOrNull()
            val message = groups["message"]?.value?.trim() ?: ""
            if (message.isNotBlank()) {
                results.add(
                    BuildDiagnostic(
                        severity = severity,
                        file = file,
                        line = line,
                        column = null,
                        code = null,
                        message = message,
                        tool = "manifest-merger"
                    )
                )
            }
        }

        for (match in executionFailedPattern.findAll(combined)) {
            val task = match.groups["task"]?.value ?: continue
            results.add(
                BuildDiagnostic(
                    severity = "error",
                    file = null,
                    line = null,
                    column = null,
                    code = null,
                    message = "Execution failed for task '$task'",
                    tool = "gradle"
                )
            )
        }

        if (results.none { it.severity == "error" }) {
            for (match in whatWentWrongPattern.findAll(combined)) {
                val message = match.groups["message"]?.value?.trim() ?: ""
                if (message.isNotBlank() && !message.contains("BUILD FAILED")) {
                    results.add(
                        BuildDiagnostic(
                            severity = "error",
                            file = null,
                            line = null,
                            column = null,
                            code = null,
                            message = message.take(500),
                            tool = "gradle"
                        )
                    )
                    break
                }
            }
        }

        return results.distinctBy { it.tool + ":" + it.file + ":" + it.line + ":" + it.message }
    }
}
