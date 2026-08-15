package io.github.blueinstruction.codehub.devtools.logcat

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
    suspend fun snapshot(filter: String?, limit: Int): List<LogcatEntry>
    suspend fun clear()
}
