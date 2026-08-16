package codehub.git.core

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class GitRef(
    val name: String,
    val commitSha: String,
    val isRemote: Boolean,
    val remote: String? = null
)

@Serializable
data class GitStatus(
    val branch: String,
    val ahead: Int,
    val behind: Int,
    val staged: List<GitFileChange>,
    val unstaged: List<GitFileChange>,
    val untracked: List<String>,
    val clean: Boolean
)

@Serializable
data class GitFileChange(
    val path: String,
    val stagedStatus: Char?,
    val unstagedStatus: Char?
)

@Serializable
data class GitCommit(
    val sha: String,
    val shortSha: String,
    val authorName: String,
    val authorEmail: String,
    val authoredAt: Long,
    val committerName: String,
    val committerEmail: String,
    val committedAt: Long,
    val message: String,
    val parents: List<String>
)

@Serializable
data class GitDiff(
    val path: String,
    val staged: Boolean,
    val hunks: List<DiffHunk>
)

@Serializable
data class DiffHunk(
    val header: String,
    val lines: List<DiffLine>
)

@Serializable
data class DiffLine(
    val kind: DiffLineKind,
    val text: String,
    val oldNumber: Int?,
    val newNumber: Int?
)

@Serializable
enum class DiffLineKind { Context, Add, Remove, Header }

interface GitService {
    suspend fun status(workspace: String): GitStatus
    suspend fun diff(workspace: String, staged: Boolean): List<GitDiff>
    suspend fun log(workspace: String, limit: Int = 50): List<GitCommit>
    suspend fun branches(workspace: String): List<GitRef>
    suspend fun createBranch(workspace: String, name: String, from: String? = null)
    suspend fun switchBranch(workspace: String, name: String)
    suspend fun deleteBranch(workspace: String, name: String, force: Boolean = false)
    suspend fun add(workspace: String, paths: List<String>)
    suspend fun commit(workspace: String, message: String, addAll: Boolean = false): String
    suspend fun push(workspace: String, remote: String = "origin", branch: String? = null)
    suspend fun pull(workspace: String, remote: String = "origin", branch: String? = null)
    suspend fun fetch(workspace: String, remote: String = "origin")
    suspend fun stash(workspace: String, message: String? = null)
    suspend fun stashPop(workspace: String)
    suspend fun merge(workspace: String, branch: String): MergeResult
    suspend fun rebase(workspace: String, onto: String)
    suspend fun tag(workspace: String, name: String, message: String?)
    suspend fun cloneRepo(url: String, into: String, depth: Int? = null): String
    suspend fun addRemote(workspace: String, name: String, url: String)
    suspend fun remotes(workspace: String): List<Pair<String, String>>
    fun refStream(workspace: String): Flow<List<GitRef>>
}

@Serializable
data class MergeResult(
    val ok: Boolean,
    val conflicts: List<String>,
    val message: String
)
