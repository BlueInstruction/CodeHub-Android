package codehub.ai.agents

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class IterationBudget(
    val parent: Int = DEFAULT_PARENT_BUDGET,
    val perSubagent: Int = DEFAULT_SUBAGENT_BUDGET
) {
    private val parentUsed = AtomicLong(0L)
    private val subagentUsed = AtomicInteger(0)
    private val mutex = Mutex()

    val remaining: Long
        get() = parent - parentUsed.get()

    val parentUsedCount: Long
        get() = parentUsed.get()

    val subagentUsedCount: Int
        get() = subagentUsed.get()

    suspend fun consume(amount: Int = 1, isSubagent: Boolean = false): Boolean = mutex.withLock {
        val parentNext = parentUsed.get() + amount
        if (parentNext > parent) return@withLock false
        if (isSubagent) {
            val subNext = subagentUsed.get() + amount
            if (subNext > perSubagent) return@withLock false
            subagentUsed.set(subNext)
        }
        parentUsed.set(parentNext)
        true
    }

    suspend fun refund(amount: Int = 1, isSubagent: Boolean = false) = mutex.withLock {
        val newParent = (parentUsed.get() - amount).coerceAtLeast(0L)
        parentUsed.set(newParent)
        if (isSubagent) {
            val newSub = (subagentUsed.get() - amount).coerceAtLeast(0)
            subagentUsed.set(newSub)
        }
        Unit
    }

    fun reset() {
        parentUsed.set(0L)
        subagentUsed.set(0)
    }

    fun resetSubagentOnly() {
        subagentUsed.set(0)
    }

    fun isExhausted(): Boolean = parentUsed.get() >= parent

    companion object {
        const val DEFAULT_PARENT_BUDGET = 500
        const val DEFAULT_SUBAGENT_BUDGET = 50
    }
}
