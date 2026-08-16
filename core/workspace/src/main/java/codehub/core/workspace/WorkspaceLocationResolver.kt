package codehub.core.workspace

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable

@Serializable
enum class WorkspaceLocationKind {
    RealFilesystem,
    TermuxAccessible,
    UnsupportedProvider,
    Invalid
}

@Serializable
data class WorkspaceLocation(
    val uri: String,
    val kind: WorkspaceLocationKind,
    val filesystemPath: String?,
    val termuxPath: String?,
    val displayName: String,
    val issues: List<String> = emptyList()
) {
    val usable: Boolean get() = kind == WorkspaceLocationKind.RealFilesystem || kind == WorkspaceLocationKind.TermuxAccessible
    val resolvedPath: String? get() = filesystemPath ?: termuxPath
}

object SafPathConverter {

    fun treeUriToPath(uriString: String): String? {
        if (!uriString.startsWith("content://")) return null
        val path = extractPathFromUri(uriString) ?: return null
        if (!path.startsWith("/tree/")) return null
        val after = path.removePrefix("/tree/")
        val decoded = decodeSafPath(after)
        return mapSafPathToFs(decoded)
    }

    fun documentUriToPath(uriString: String): String? {
        if (!uriString.startsWith("content://")) return null
        val path = extractPathFromUri(uriString) ?: return null
        if (!path.startsWith("/document/")) return null
        val after = path.removePrefix("/document/")
        val decoded = decodeSafPath(after)
        return mapSafPathToFs(decoded)
    }

    fun uriToPath(uriString: String): String? {
        return treeUriToPath(uriString) ?: documentUriToPath(uriString)
    }

    private fun extractPathFromUri(uriString: String): String? {
        val pathStart = uriString.indexOf("//")
        if (pathStart < 0) return null
        val afterScheme = uriString.substring(pathStart + 2)
        val queryStart = afterScheme.indexOf('?')
        val fragmentStart = afterScheme.indexOf('#')
        val end = listOf(queryStart, fragmentStart).filter { it >= 0 }.minOrNull() ?: afterScheme.length
        val authorityAndPath = afterScheme.substring(0, end)
        val firstSlash = authorityAndPath.indexOf('/')
        if (firstSlash < 0) return null
        return authorityAndPath.substring(firstSlash)
    }

    private fun decodeSafPath(encoded: String): String {
        return encoded
            .replace("%3A", ":")
            .replace("%2F", "/")
            .replace("%20", " ")
            .replace("%25", "%")
    }

    private fun mapSafPathToFs(decoded: String): String {
        return when {
            decoded.startsWith("primary:") || decoded.startsWith("primary/") -> {
                val rest = decoded.substringAfter(":")
                "/storage/emulated/0/$rest"
            }
            decoded.contains(":") -> {
                val volume = decoded.substringBefore(":")
                val rest = decoded.substringAfter(":")
                "/storage/$volume/$rest"
            }
            else -> "/storage/$decoded"
        }
    }
}

@Singleton
class WorkspaceLocationResolver @Inject constructor(
    private val context: Context
) {

    fun resolve(uri: Uri): WorkspaceLocation {
        val uriString = uri.toString()
        val path = SafPathConverter.uriToPath(uriString)
            ?: uri.path
            ?: return WorkspaceLocation(
                uri = uriString,
                kind = WorkspaceLocationKind.Invalid,
                filesystemPath = null,
                termuxPath = null,
                displayName = uri.lastPathSegment ?: "(unknown)",
                issues = listOf("URI has no resolvable path")
            )

        return resolveFilesystemPath(path, uriString)
    }

    fun resolvePath(path: String): WorkspaceLocation =
        resolveFilesystemPath(path, path)

    private fun resolveFilesystemPath(path: String, uriString: String): WorkspaceLocation {
        val file = File(path)
        val parent = file.parentFile
        val displayName = file.name

        if (!isExternalStoragePath(path) && !isTermuxPath(path)) {
            return WorkspaceLocation(
                uri = uriString,
                kind = WorkspaceLocationKind.UnsupportedProvider,
                filesystemPath = path,
                termuxPath = null,
                displayName = displayName,
                issues = listOf("Path '$path' is not in external storage or Termux-accessible area. " +
                    "Gradle (running via Termux) cannot access app-private directories, content URIs, or SAF-mounted providers.")
            )
        }

        val termuxPath = resolveTermuxEquivalent(path)
        val termuxAccessible = termuxPath != null || isTermuxPath(path)

        val kind = when {
            isTermuxPath(path) -> WorkspaceLocationKind.TermuxAccessible
            isExternalStoragePath(path) && termuxAccessible -> WorkspaceLocationKind.TermuxAccessible
            isExternalStoragePath(path) -> WorkspaceLocationKind.RealFilesystem
            else -> WorkspaceLocationKind.UnsupportedProvider
        }

        val issues = mutableListOf<String>()
        if (kind == WorkspaceLocationKind.RealFilesystem) {
            issues += "Path is in external storage but may not be accessible to Termux without MANAGE_EXTERNAL_STORAGE or symlink setup."
        }
        if (!file.exists() && parent != null && !parent.canWrite()) {
            issues += "Parent directory '${parent.absolutePath}' is not writable."
        }

        return WorkspaceLocation(
            uri = uriString,
            kind = kind,
            filesystemPath = path,
            termuxPath = termuxPath,
            displayName = displayName,
            issues = issues
        )
    }

    private fun isExternalStoragePath(path: String): Boolean {
        return path.startsWith("/storage/emulated/") ||
            path.startsWith("/sdcard/") ||
            path.startsWith("/storage/") ||
            path.startsWith("/data/media/")
    }

    private fun isTermuxPath(path: String): Boolean {
        return path.startsWith("/data/data/com.termux/") ||
            path.startsWith("/data/user/0/com.termux/")
    }

    private fun resolveTermuxEquivalent(path: String): String? {
        return when {
            path.startsWith("/storage/emulated/0/") -> {
                "/data/data/com.termux/files/home/storage/shared/${path.removePrefix("/storage/emulated/0/")}"
            }
            path.startsWith("/sdcard/") -> {
                "/data/data/com.termux/files/home/storage/shared/${path.removePrefix("/sdcard/")}"
            }
            isTermuxPath(path) -> path
            else -> null
        }
    }

    fun listCandidateParentDirectories(): List<String> {
        val candidates = mutableListOf<String>()
        val termuxShared = "/data/data/com.termux/files/home/storage/shared"
        if (File(termuxShared).isDirectory) candidates += termuxShared
        val externalStorage = "/storage/emulated/0"
        if (File(externalStorage).isDirectory) candidates += externalStorage
        val documents = "/storage/emulated/0/Documents"
        if (File(documents).isDirectory) candidates += documents
        candidates += "/storage/emulated/0/CodeHub/workspaces"
        return candidates.distinct()
    }
}
