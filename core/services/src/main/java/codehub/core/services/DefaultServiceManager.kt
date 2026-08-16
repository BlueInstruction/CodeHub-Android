package codehub.core.services

import codehub.core.diagnostics.DiagnosticEvent
import codehub.core.diagnostics.DiagnosticEventKind
import codehub.core.diagnostics.DiagnosticSeverity
import codehub.core.diagnostics.DiagnosticSink
import codehub.core.diagnostics.DiagnosticStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultServiceManager @Inject constructor(
    private val diagnostics: DiagnosticSink
) : ServiceManager {

    private val registry = ConcurrentHashMap<String, ManagedService>()
    private val mutex = Mutex()

    override fun register(service: ManagedService) {
        registry[service.name] = service
    }

    override fun services(): List<ManagedService> = registry.values.toList().sortedBy { it.name }

    override fun statuses(): Flow<List<ServiceStatus>> = flow {
        val services = services()
        if (services.isEmpty()) {
            emit(emptyList())
            return@flow
        }
        val perService = services.map { svc -> svc.status() }
        kotlinx.coroutines.flow.combine(perService) { array ->
            array.toList()
        }.collect { emit(it) }
    }

    override suspend fun startAll() = mutex.withLock {
        registry.values.sortedBy { it.name }.forEach { svc ->
            runCatching { svc.start() }.onSuccess {
                diagnostics.emit(
                    DiagnosticEvent.now(
                        kind = DiagnosticEventKind.ServiceStarted,
                        severity = DiagnosticSeverity.Info,
                        status = DiagnosticStatus.Ok,
                        source = "ServiceManager",
                        message = "${svc.name} started"
                    )
                )
            }.onFailure { e ->
                diagnostics.emit(
                    DiagnosticEvent.now(
                        kind = DiagnosticEventKind.ServiceFailed,
                        severity = DiagnosticSeverity.Error,
                        status = DiagnosticStatus.Failed,
                        source = "ServiceManager",
                        message = "Failed to start ${svc.name}",
                        reason = e.message
                    )
                )
            }
        }
    }

    override suspend fun stopAll() = mutex.withLock {
        registry.values.sortedByDescending { it.name }.forEach { svc ->
            runCatching { svc.stop() }.onSuccess {
                diagnostics.emit(
                    DiagnosticEvent.now(
                        kind = DiagnosticEventKind.ServiceStopped,
                        severity = DiagnosticSeverity.Info,
                        status = DiagnosticStatus.Ok,
                        source = "ServiceManager",
                        message = "${svc.name} stopped"
                    )
                )
            }.onFailure { e ->
                diagnostics.emit(
                    DiagnosticEvent.now(
                        kind = DiagnosticEventKind.ServiceFailed,
                        severity = DiagnosticSeverity.Warn,
                        status = DiagnosticStatus.Failed,
                        source = "ServiceManager",
                        message = "Failed to stop ${svc.name}",
                        reason = e.message
                    )
                )
            }
        }
    }

    override suspend fun restart(name: String) {
        mutex.withLock {
            registry[name]?.restart()
        }
    }

    override suspend fun get(name: String): ManagedService? = registry[name]
}
