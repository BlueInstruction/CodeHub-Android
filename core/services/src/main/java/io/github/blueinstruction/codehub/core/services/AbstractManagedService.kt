package io.github.blueinstruction.codehub.core.services

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

abstract class AbstractManagedService(
    override val name: String
) : ManagedService {

    private val state = MutableStateFlow(
        ServiceStatus(
            name = name,
            state = ServiceState.Stopped,
            startedAt = null,
            lastError = null
        )
    )

    protected val logStream = MutableSharedFlow<String>(extraBufferCapacity = 256)

    override fun status() = state.asStateFlow()

    override fun logs(): Flow<String> = logStream

    protected fun setState(newState: ServiceState, error: String? = null, pid: Long? = null) {
        state.value = state.value.copy(
            state = newState,
            startedAt = if (newState == ServiceState.Running) System.currentTimeMillis() else state.value.startedAt,
            lastError = error,
            pid = pid ?: state.value.pid
        )
    }

    protected fun emitLog(line: String) {
        logStream.tryEmit(line)
    }

    override suspend fun start() {
        setState(ServiceState.Starting)
        runCatching { onStart() }.onSuccess {
            setState(ServiceState.Running)
        }.onFailure { e ->
            setState(ServiceState.Failed, error = e.message)
        }
    }

    override suspend fun stop() {
        setState(ServiceState.Stopping)
        runCatching { onStop() }.onSuccess {
            setState(ServiceState.Stopped)
        }.onFailure { e ->
            setState(ServiceState.Failed, error = e.message)
        }
    }

    override suspend fun restart() {
        stop()
        start()
    }

    protected abstract suspend fun onStart()

    protected abstract suspend fun onStop()

    override suspend fun health(): ServiceHealth {
        return when (state.value.state) {
            ServiceState.Running -> ServiceHealth.Healthy
            ServiceState.Starting -> ServiceHealth.Degraded
            ServiceState.Stopping -> ServiceHealth.Degraded
            ServiceState.Stopped -> ServiceHealth.Unknown
            ServiceState.Failed -> ServiceHealth.Unhealthy
        }
    }
}
