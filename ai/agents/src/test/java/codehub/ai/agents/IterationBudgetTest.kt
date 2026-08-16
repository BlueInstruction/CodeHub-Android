package codehub.ai.agents

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class IterationBudgetTest {

    @Test
    fun `fresh budget has full remaining`() = runTest {
        val budget = IterationBudget(parent = 100, perSubagent = 10)
        assertThat(budget.remaining).isEqualTo(100L)
        assertThat(budget.isExhausted()).isFalse()
    }

    @Test
    fun `consume decrements remaining`() = runTest {
        val budget = IterationBudget(parent = 100, perSubagent = 10)
        val ok = budget.consume(5)
        assertThat(ok).isTrue()
        assertThat(budget.remaining).isEqualTo(95L)
        assertThat(budget.parentUsedCount).isEqualTo(5L)
    }

    @Test
    fun `consume over limit returns false`() = runTest {
        val budget = IterationBudget(parent = 5, perSubagent = 10)
        val first = budget.consume(3)
        val second = budget.consume(4)
        assertThat(first).isTrue()
        assertThat(second).isFalse()
        assertThat(budget.remaining).isEqualTo(2L)
    }

    @Test
    fun `refund restores remaining`() = runTest {
        val budget = IterationBudget(parent = 100, perSubagent = 10)
        budget.consume(10)
        budget.refund(3)
        assertThat(budget.parentUsedCount).isEqualTo(7L)
        assertThat(budget.remaining).isEqualTo(93L)
    }

    @Test
    fun `refund below zero is clamped`() = runTest {
        val budget = IterationBudget(parent = 100, perSubagent = 10)
        budget.consume(5)
        budget.refund(10)
        assertThat(budget.parentUsedCount).isEqualTo(0L)
        assertThat(budget.remaining).isEqualTo(100L)
    }

    @Test
    fun `subagent consume is bounded by perSubagent`() = runTest {
        val budget = IterationBudget(parent = 100, perSubagent = 5)
        val ok1 = budget.consume(3, isSubagent = true)
        val ok2 = budget.consume(3, isSubagent = true)
        assertThat(ok1).isTrue()
        assertThat(ok2).isFalse()
        assertThat(budget.subagentUsedCount).isEqualTo(3)
        assertThat(budget.parentUsedCount).isEqualTo(3L)
    }

    @Test
    fun `reset clears parent counter`() = runTest {
        val budget = IterationBudget(parent = 100, perSubagent = 10)
        budget.consume(20, isSubagent = true)
        budget.reset()
        assertThat(budget.parentUsedCount).isEqualTo(0L)
        assertThat(budget.subagentUsedCount).isEqualTo(0)
        assertThat(budget.remaining).isEqualTo(100L)
    }

    @Test
    fun `resetSubagentOnly keeps parent`() = runTest {
        val budget = IterationBudget(parent = 100, perSubagent = 10)
        budget.consume(20, isSubagent = true)
        budget.resetSubagentOnly()
        assertThat(budget.parentUsedCount).isEqualTo(20L)
        assertThat(budget.subagentUsedCount).isEqualTo(0)
    }

    @Test
    fun `default budgets are 500 parent and 50 subagent`() {
        val budget = IterationBudget()
        assertThat(budget.parent).isEqualTo(500)
        assertThat(budget.perSubagent).isEqualTo(50)
    }

    @Test
    fun `isExhausted returns true when parent hits cap`() = runTest {
        val budget = IterationBudget(parent = 5, perSubagent = 5)
        budget.consume(5)
        assertThat(budget.isExhausted()).isTrue()
        assertThat(budget.remaining).isEqualTo(0L)
    }
}
