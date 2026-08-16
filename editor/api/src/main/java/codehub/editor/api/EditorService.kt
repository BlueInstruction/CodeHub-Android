package codehub.editor.api

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
enum class EditorBackend { CodeServer, VsCodeWeb, External }

@Serializable
data class EditorEndpoint(
    val backend: EditorBackend,
    val url: String,
    val port: Int?,
    val token: String?,
    val requiresAuth: Boolean
)

@Serializable
sealed interface EditorEvent {
    @Serializable data class Started(val endpoint: EditorEndpoint) : EditorEvent
    @Serializable data class Stopped(val backend: EditorBackend) : EditorEvent
    @Serializable data class Failed(val backend: EditorBackend, val message: String) : EditorEvent
    @Serializable data class Log(val backend: EditorBackend, val line: String) : EditorEvent
}

interface EditorService {
    val current: Flow<EditorEndpoint?>
    val events: Flow<EditorEvent>
    suspend fun start(backend: EditorBackend): EditorEndpoint
    suspend fun stop()
    suspend fun restart()
}
