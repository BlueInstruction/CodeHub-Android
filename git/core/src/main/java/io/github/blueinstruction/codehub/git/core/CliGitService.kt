package io.github.blueinstruction.codehub.git.core

import io.github.blueinstruction.codehub.core.diagnostics.DiagnosticEvent
import io.github.blueinstruction.codehub.core.diagnostics.DiagnosticEventKind
import io.github.blueinstruction.codehub.core.diagnostics.DiagnosticSeverity
import io.github.blueinstruction.codehub.core.diagnostics.DiagnosticSink
import io.github.blueinstruction.codehub.core.diagnostics.DiagnosticStatus
import io.github.blueinstruction.codehub.core.process.ProcessRunner
import io.github.blueinstruction.codehub.core.process.ProcessSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CliGitService @Inject constructor(
    private val processRunner: ProcessRunner,
    private val diagnostics: DiagnosticSink
) : GitService {

    override suspend fun status(workspace: String): GitStatus {
        val out = git(workspace, listOf("status", "--porcelain=v2", "--branch"))
        val lines = out.stdout.lines().filter { it.isNotBlank() }
        var branch = "(unknown)"
        var ahead = 0
        var behind = 0
        val staged = mutableListOf<GitFileChange>()
        val unstaged = mutableListOf<GitFileChange>()
        val untracked = mutableListOf<String>()
        lines.forEach { line ->
            when {
                line.startsWith("# branch.head ") -> branch = line.substringAfter("# branch.head ").trim()
                line.startsWith("# branch.ab ") -> {
                    val parts = line.substringAfter("# branch.ab ").trim().split(" ")
                    parts.forEach { p ->
                        if (p.startsWith("+")) ahead = p.drop(1).toIntOrNull() ?: 0
                        if (p.startsWith("-")) behind = p.drop(1).toIntOrNull() ?: 0
                    }
                }
                line.startsWith("u ") || line.startsWith("1 ") || line.startsWith("2 ") -> {
                    val parts = line.split(" ")
                    val xy = parts.getOrNull(1) ?: ""
                    val path = parts.drop(8).joinToString(" ").trim()
                    val stagedStatus = xy.getOrNull(0)
                    val unstagedStatus = xy.getOrNull(1)
                    if (stagedStatus != null && stagedStatus != '.') {
                        staged.add(GitFileChange(path = path, stagedStatus = stagedStatus, unstagedStatus = unstagedStatus))
                    }
                    if (unstagedStatus != null && unstagedStatus != '.') {
                        unstaged.add(GitFileChange(path = path, stagedStatus = stagedStatus, unstagedStatus = unstagedStatus))
                    }
                }
                line.startsWith("? ") -> {
                    untracked.add(line.substring(2).trim())
                }
            }
        }
        val clean = staged.isEmpty() && unstaged.isEmpty() && untracked.isEmpty()
        return GitStatus(
            branch = branch,
            ahead = ahead,
            behind = behind,
            staged = staged,
            unstaged = unstaged,
            untracked = untracked,
            clean = clean
        )
    }

    override suspend fun diff(workspace: String, staged: Boolean): List<GitDiff> {
        val args = if (staged) listOf("diff", "--cached", "--numstat", "--") else listOf("diff", "--numstat", "--")
        val numstat = git(workspace, args)
        val paths = numstat.stdout.lines().filter { it.isNotBlank() }.map { it.split("\t").lastOrNull() ?: "" }.filter { it.isNotBlank() }
        return paths.map { path ->
            val patch = git(workspace, listOf("diff", if (staged) "--cached" else "", "--", path)).stdout
            GitDiff(path = path, staged = staged, hunks = parseHunks(patch))
        }
    }

    override suspend fun log(workspace: String, limit: Int): List<GitCommit> {
        val sep = "<<CODEHUB>>"
        val format = listOf("%H", "%h", "%an", "%ae", "%at", "%cn", "%ce", "%ct", "%s", "%P").joinToString(sep)
        val out = git(workspace, listOf("log", "-n", limit.toString(), "--format=$format"))
        return out.stdout.lines().filter { it.isNotBlank() }.map { line ->
            val p = line.split(sep)
            GitCommit(
                sha = p.getOrNull(0).orEmpty(),
                shortSha = p.getOrNull(1).orEmpty(),
                authorName = p.getOrNull(2).orEmpty(),
                authorEmail = p.getOrNull(3).orEmpty(),
                authoredAt = p.getOrNull(4)?.toLongOrNull() ?: 0L,
                committerName = p.getOrNull(5).orEmpty(),
                committerEmail = p.getOrNull(6).orEmpty(),
                committedAt = p.getOrNull(7)?.toLongOrNull() ?: 0L,
                message = p.getOrNull(8).orEmpty(),
                parents = p.getOrNull(9)?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
            )
        }
    }

    override suspend fun branches(workspace: String): List<GitRef> {
        val out = git(workspace, listOf("for-each-ref", "--format=%(refname)%09%(objectname)%09%(upstream:short)", "refs/heads/", "refs/remotes/"))
        return out.stdout.lines().filter { it.isNotBlank() }.map { line ->
            val parts = line.split("\t")
            val ref = parts.getOrNull(0).orEmpty()
            val name = ref.substringAfter("refs/heads/").substringAfter("refs/remotes/")
            val isRemote = ref.startsWith("refs/remotes/")
            val remote = if (isRemote) ref.substringAfter("refs/remotes/").substringBefore("/") else null
            GitRef(name = name, commitSha = parts.getOrNull(1).orEmpty(), isRemote = isRemote, remote = remote)
        }
    }

    override suspend fun createBranch(workspace: String, name: String, from: String?) {
        git(workspace, listOf("branch", name) + listOfNotNull(from))
    }

    override suspend fun switchBranch(workspace: String, name: String) {
        git(workspace, listOf("switch", name))
    }

    override suspend fun deleteBranch(workspace: String, name: String, force: Boolean) {
        git(workspace, listOf("branch", if (force) "-D" else "-d", name))
    }

    override suspend fun add(workspace: String, paths: List<String>) {
        git(workspace, listOf("add", "--") + paths)
    }

    override suspend fun commit(workspace: String, message: String, addAll: Boolean): String {
        if (addAll) git(workspace, listOf("add", "-A"))
        val result = git(workspace, listOf("commit", "-m", message))
        val sha = git(workspace, listOf("rev-parse", "HEAD")).stdout.trim()
        diagnostics.emit(
            DiagnosticEvent.now(
                kind = DiagnosticEventKind.RuntimeInitialization,
                severity = DiagnosticSeverity.Info,
                status = DiagnosticStatus.Ok,
                source = "GitService",
                message = "Committed $sha",
                attributes = mapOf("workspace" to workspace)
            )
        )
        return sha
    }

    override suspend fun push(workspace: String, remote: String, branch: String?) {
        git(workspace, listOf("push", remote, branch ?: "HEAD"))
    }

    override suspend fun pull(workspace: String, remote: String, branch: String?) {
        git(workspace, listOf("pull", remote, branch ?: "HEAD"))
    }

    override suspend fun fetch(workspace: String, remote: String) {
        git(workspace, listOf("fetch", remote))
    }

    override suspend fun stash(workspace: String, message: String?) {
        git(workspace, listOf("stash", "push") + listOfNotNull(message?.let { "-m" }, message))
    }

    override suspend fun stashPop(workspace: String) {
        git(workspace, listOf("stash", "pop"))
    }

    override suspend fun merge(workspace: String, branch: String): MergeResult {
        val result = git(workspace, listOf("merge", "--no-stat", branch))
        val conflicts = if (result.exitCode != 0) {
            git(workspace, listOf("diff", "--name-only", "--diff-filter=U")).stdout.lines().filter { it.isNotBlank() }
        } else emptyList()
        return MergeResult(ok = result.exitCode == 0, conflicts = conflicts, message = result.stdout)
    }

    override suspend fun rebase(workspace: String, onto: String) {
        git(workspace, listOf("rebase", onto))
    }

    override suspend fun tag(workspace: String, name: String, message: String?) {
        git(workspace, listOf("tag", "-a", name) + listOfNotNull(message?.let { "-m" }, message))
    }

    override suspend fun cloneRepo(url: String, into: String, depth: Int?): String {
        val args = mutableListOf("clone", url, into)
        depth?.let { args.addAll(listOf("--depth", it.toString())) }
        val result = git(System.getProperty("user.dir"), args)
        if (result.exitCode != 0) error(result.stderr)
        return into
    }

    override suspend fun addRemote(workspace: String, name: String, url: String) {
        git(workspace, listOf("remote", "add", name, url))
    }

    override suspend fun remotes(workspace: String): List<Pair<String, String>> {
        val out = git(workspace, listOf("remote", "-v"))
        return out.stdout.lines().filter { it.isNotBlank() }.mapNotNull { line ->
            val parts = line.split("\t")
            val name = parts.getOrNull(0) ?: return@mapNotNull null
            val url = parts.getOrNull(1)?.substringBefore(" ") ?: return@mapNotNull null
            name to url
        }.distinct()
    }

    override fun refStream(workspace: String): Flow<List<GitRef>> = flow {
        emit(branches(workspace))
    }

    private suspend fun git(workspace: String, args: List<String>): io.github.blueinstruction.codehub.core.process.ProcessResult {
        if (!File(workspace, ".git").exists() && args.firstOrNull() != "clone") {
            return io.github.blueinstruction.codehub.core.process.ProcessResult(
                exitCode = -1,
                stdout = "",
                stderr = "Not a git repository: $workspace",
                durationMs = 0
            )
        }
        val spec = ProcessSpec(
            command = listOf("git") + args,
            workingDirectory = workspace,
            environment = emptyMap(),
            timeoutMs = 60_000
        )
        return processRunner.run(spec)
    }

    private fun parseHunks(patch: String): List<DiffHunk> {
        val hunks = mutableListOf<DiffHunk>()
        var currentHeader: String? = null
        var currentLines = mutableListOf<DiffLine>()
        var oldLine = 0
        var newLine = 0
        patch.lines().forEach { line ->
            if (line.startsWith("@@")) {
                if (currentHeader != null) {
                    hunks.add(DiffHunk(currentHeader!!, currentLines.toList()))
                }
                currentHeader = line
                currentLines = mutableListOf()
                val match = Regex("""@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@""").find(line)
                oldLine = match?.groups?.get(1)?.value?.toIntOrNull() ?: 0
                newLine = match?.groups?.get(2)?.value?.toIntOrNull() ?: 0
            } else if (currentHeader != null) {
                when {
                    line.startsWith("+++") || line.startsWith("---") -> Unit
                    line.startsWith("+") -> {
                        currentLines.add(DiffLine(DiffLineKind.Add, line.drop(1), null, newLine))
                        newLine++
                    }
                    line.startsWith("-") -> {
                        currentLines.add(DiffLine(DiffLineKind.Remove, line.drop(1), oldLine, null))
                        oldLine++
                    }
                    line.startsWith(" ") || line.isEmpty() -> {
                        currentLines.add(DiffLine(DiffLineKind.Context, line.drop(1), oldLine, newLine))
                        oldLine++
                        newLine++
                    }
                }
            }
        }
        if (currentHeader != null) {
            hunks.add(DiffHunk(currentHeader!!, currentLines.toList()))
        }
        return hunks
    }
}
