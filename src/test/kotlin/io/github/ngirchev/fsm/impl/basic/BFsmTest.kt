package io.github.ngirchev.fsm.impl.basic

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import io.github.ngirchev.fsm.StateContext
import io.github.ngirchev.fsm.exception.FsmTransitionFailedException
import io.github.ngirchev.fsm.exception.FsmException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BFsmTest {

    private class SimpleStateContext(override var state: String, override var currentTransition: io.github.ngirchev.fsm.Transition<String>? = null) : StateContext<String>

    @Test
    fun constructorWithStateAndNullAutoTransitionShouldUseTableValue() {
        val table = BTransitionTable.Builder<String>()
            .add("from", "to")
            .autoTransitionEnabled(true)
            .build()

        val fsm = BFsm("initial", table, autoTransitionEnabled = null)

        assertEquals("initial", fsm.getState())
        assertEquals(true, fsm.autoTransitionEnabled)
    }

    @Test
    fun constructorWithContextAndNullAutoTransitionShouldUseTableValue() {
        val table = BTransitionTable.Builder<String>()
            .add("from", "to")
            .autoTransitionEnabled(false)
            .build()

        val context = SimpleStateContext("initial")
        val fsm = BFsm(context, table, autoTransitionEnabled = null)

        assertEquals("initial", fsm.getState())
        assertEquals(false, fsm.autoTransitionEnabled)
    }

    @Test
    fun toStateWhenTransitionExistsShouldChangeState() {
        val table = BTransitionTable.Builder<String>()
            .add("from", "to")
            .build()

        val fsm = BFsm("from", table)
        fsm.toState("to")

        assertEquals("to", fsm.getState())
    }

    @Test
    fun toStateWhenTransitionDoesNotExistShouldThrowException() {
        val table = BTransitionTable.Builder<String>()
            .add("from", "to")
            .build()

        val fsm = BFsm("from", table)

        assertThrows(FsmTransitionFailedException::class.java) {
            fsm.toState("nonexistent")
        }
    }

    @Test
    fun toStateWhenFromStateDoesNotMatchShouldThrowException() {
        val table = BTransitionTable.Builder<String>()
            .add("from", "to")
            .build()

        val fsm = BFsm("wrong", table)

        assertThrows(FsmException::class.java) {
            fsm.toState("to")
        }
    }

    @Test
    fun toStateWithAutoTransitionEnabledShouldPerformAutoTransitions() {
        val table = BTransitionTable.Builder<String>()
            .autoTransitionEnabled(true)
            .add("from", "intermediate")
            .add("intermediate", "to")
            .build()

        val fsm = BFsm("from", table, autoTransitionEnabled = true)
        fsm.toState("intermediate")

        assertEquals("to", fsm.getState())
    }

    @Test
    fun toStateWithAutoTransitionDisabledShouldNotPerformAutoTransitions() {
        val table = BTransitionTable.Builder<String>()
            .autoTransitionEnabled(false)
            .add("from", "intermediate")
            .add("intermediate", "to")
            .build()

        val fsm = BFsm("from", table, autoTransitionEnabled = false)
        fsm.toState("intermediate")

        assertEquals("intermediate", fsm.getState())
    }

    @Test
    fun toStateWithTimeoutShouldWait() {
        val start = System.currentTimeMillis()
        val table = BTransitionTable.Builder<String>()
            .add("from", io.github.ngirchev.fsm.To("to", timeout = io.github.ngirchev.fsm.Timeout(1)))
            .build()

        val fsm = BFsm("from", table)
        fsm.toState("to")
        val end = System.currentTimeMillis()

        assertEquals("to", fsm.getState())
        assertTrue(end - start >= 1000)
    }

    @Test
    fun toStateWithActionsShouldExecuteActions() {
        var actionCalled = false
        var postActionCalled = false

        val table = BTransitionTable.Builder<String>()
            .from("from")
            .to("to")
            .action { actionCalled = true }
            .postAction { postActionCalled = true }
            .end()
            .build()

        val fsm = BFsm("from", table)
        fsm.toState("to")

        assertEquals("to", fsm.getState())
        assertTrue(actionCalled)
        assertTrue(postActionCalled)
    }

    @Test
    fun toStateWithNullTimeoutShouldNotWait() {
        val start = System.currentTimeMillis()
        val table = BTransitionTable.Builder<String>()
            .add("from", "to")
            .build()

        val fsm = BFsm("from", table)
        fsm.toState("to")
        val end = System.currentTimeMillis()

        assertEquals("to", fsm.getState())
        assertTrue(end - start < 1000)
    }

    @Test
    fun toStateWithMultipleAutoTransitionsShouldPerformAll() {
        val table = BTransitionTable.Builder<String>()
            .autoTransitionEnabled(true)
            .add("from", "intermediate1")
            .add("intermediate1", "intermediate2")
            .add("intermediate2", "to")
            .build()

        val fsm = BFsm("from", table, autoTransitionEnabled = true)
        fsm.toState("intermediate1")

        assertEquals("to", fsm.getState())
    }
}
