package io.github.blueinstruction.codehub.core.diagnostics

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentLinkedDeque
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InMemoryDiagnosticSink @Inject constructor() : DiagnosticSink {

    private val flow = MutableSharedFlow<DiagnosticEvent>(
        replay = 256,
        extraBufferCapacity = 512
    )
    private val ring = ConcurrentLinkedDeque<DiagnosticEvent>()
    private val maxRingSize = 2048

    override fun emit(event: DiagnosticEvent) {
        ring.addLast(event)
        while (ring.size > maxRingSize) ring.pollFirst()
        flow.tryEmit(event)
    }

    override fun events(): SharedFlow<DiagnosticEvent> = flow.asSharedFlow()

    override fun snapshot(): List<DiagnosticEvent> = ring.toList()
}
