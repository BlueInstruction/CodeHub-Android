package codehub.devtools.logcat

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class LogcatEntry(
    val timestamp: String,
    val pid: Int?,
    val tid: Int?,
    val level: Char,
    val tag: String,
    val message: String,
    val raw: String
)

interface LogcatService {
    fun stream(filter: String?): Flow<LogcatEntry>
    fun streamForPid(pid: Int, filter: String? = null): Flow<LogcatEntry>
    fun streamForPackage(packageName: String, filter: String? = null): Flow<LogcatEntry>
    suspend fun snapshot(filter: String?, limit: Int): List<LogcatEntry>
    suspend fun snapshotForPid(pid: Int, filter: String?, limit: Int): List<LogcatEntry>
    suspend fun snapshotForPackage(packageName: String, filter: String?, limit: Int): List<LogcatEntry>
    suspend fun resolvePid(packageName: String): Int?
    suspend fun clear()
}
