package io.github.blueinstruction.codehub.editor.api

interface EditorBackendProvider {
    val backend: EditorBackend
    suspend fun isAvailable(): Boolean
    suspend fun start(): EditorEndpoint
    suspend fun stop()
}

class EditorNotAvailableException(backend: EditorBackend) :
    RuntimeException("Editor backend $backend is not available on this device")
