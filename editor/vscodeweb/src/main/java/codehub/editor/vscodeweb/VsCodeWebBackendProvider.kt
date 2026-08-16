package codehub.editor.vscodeweb

import codehub.editor.api.EditorBackend
import codehub.editor.api.EditorEndpoint
import codehub.editor.api.EditorBackendProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VsCodeWebBackendProvider @Inject constructor() : EditorBackendProvider {

    @Volatile private var running = false

    override val backend: EditorBackend = EditorBackend.VsCodeWeb

    override suspend fun isAvailable(): Boolean = true

    override suspend fun start(): EditorEndpoint {
        running = true
        return EditorEndpoint(
            backend = EditorBackend.VsCodeWeb,
            url = "https://vscode.dev",
            port = null,
            token = null,
            requiresAuth = false
        )
    }

    override suspend fun stop() {
        running = false
    }
}
