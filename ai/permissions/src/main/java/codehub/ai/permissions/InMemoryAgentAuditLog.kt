package codehub.ai.permissions

import java.util.concurrent.ConcurrentLinkedDeque
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InMemoryAgentAuditLog @Inject constructor() : AgentAuditLog {

    private val ring = ConcurrentLinkedDeque<AuditEntry>()
    private val maxSize = 4096

    override fun record(entry: AuditEntry) {
        ring.addLast(entry)
        while (ring.size > maxSize) ring.pollFirst()
    }

    override fun recent(limit: Int): List<AuditEntry> =
        ring.toList().takeLast(limit)
}
