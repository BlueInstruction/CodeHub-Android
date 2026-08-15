package io.github.blueinstruction.codehub.core.services

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
enum class ServiceState { Stopped, Starting, Running, Stopping, Failed }

@Serializable
data class ServiceStatus(
    val name: String,
    val state: ServiceState,
    val startedAt: Long?,
    val lastError: String?,
    val pid: Long? = null
)

interface ManagedService {
    val name: String
    fun status(): Flow<ServiceStatus>
    suspend fun start()
    suspend fun stop()
    suspend fun restart()
    suspend fun health(): ServiceHealth
    fun logs(): Flow<String>
}

enum class ServiceHealth { Healthy, Degraded, Unhealthy, Unknown }

interface ServiceManager {
    fun register(service: ManagedService)
    fun services(): List<ManagedService>
    fun statuses(): Flow<List<ServiceStatus>>
    suspend fun startAll()
    suspend fun stopAll()
    suspend fun restart(name: String)
    suspend fun get(name: String): ManagedService?
}
