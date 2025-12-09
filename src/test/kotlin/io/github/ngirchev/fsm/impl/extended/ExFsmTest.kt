package io.github.ngirchev.fsm.impl.extended

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import io.github.ngirchev.fsm.exception.FsmEventSourcingTransitionFailedException
import io.github.ngirchev.fsm.StateContext
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExFsmTest {

    private class SimpleStateContext(override var state: String, override var currentTransition: io.github.ngirchev.fsm.Transition<String>? = null) : StateContext<String>

    @Test
    fun constructorWithStateShouldCreateFsm() {
        val table = ExTransitionTable.Builder<String, String>()
            .add("from", "event", "to")
            .build()

        val fsm = ExFsm("initial", table)

        assertEquals("initial", fsm.getState())
    }

    @Test
    fun constructorWithStateAndAutoTransitionEnabledShouldCreateFsm() {
        val table = ExTransitionTable.Builder<String, String>()
            .add("from", "event", "to")
            .build()

        val fsm = ExFsm("initial", table, autoTransitionEnabled = false)

        assertEquals("initial", fsm.getState())
    }

    @Test
    fun constructorWithContextShouldCreateFsm() {
        val table = ExTransitionTable.Builder<String, String>()
            .add("from", "event", "to")
            .build()

        val context = SimpleStateContext("initial")
        val fsm = ExFsm(context, table)

        assertEquals("initial", fsm.getState())
    }

    @Test
    fun constructorWithContextAndAutoTransitionEnabledShouldCreateFsm() {
        val table = ExTransitionTable.Builder<String, String>()
            .add("from", "event", "to")
            .build()

        val context = SimpleStateContext("initial")
        val fsm = ExFsm(context, table, autoTransitionEnabled = false)

        assertEquals("initial", fsm.getState())
    }

    @Test
    fun onEventWhenTransitionExistsShouldChangeState() {
        val table = ExTransitionTable.Builder<String, String>()
            .add("from", "event", "to")
            .build()

        val fsm = ExFsm("from", table)
        fsm.onEvent("event")

        assertEquals("to", fsm.getState())
    }

    @Test
    fun onEventWhenTransitionDoesNotExistShouldThrowException() {
        val table = ExTransitionTable.Builder<String, String>()
            .add("from", "event", "to")
            .build()

        val fsm = ExFsm("from", table)

        assertThrows(FsmEventSourcingTransitionFailedException::class.java) {
            fsm.onEvent("nonexistent")
        }
    }

    @Test
    fun onEventWithAutoTransitionEnabledShouldPerformAutoTransitions() {
        val table = ExTransitionTable.Builder<String, String>()
            .autoTransitionEnabled(true)
            .add("from", "event", "intermediate")
            .add("intermediate", null, "to")
            .build()

        val fsm = ExFsm("from", table, autoTransitionEnabled = true)
        fsm.onEvent("event")

        assertEquals("to", fsm.getState())
    }

    @Test
    fun onEventWithAutoTransitionDisabledShouldNotPerformAutoTransitions() {
        val table = ExTransitionTable.Builder<String, String>()
            .autoTransitionEnabled(false)
            .add("from", "event", "intermediate")
            .add("intermediate", null, "to")
            .build()

        val fsm = ExFsm("from", table, autoTransitionEnabled = false)
        fsm.onEvent("event")

        assertEquals("intermediate", fsm.getState())
    }

    @Test
    fun toStateWithAutoTransitionEnabledShouldPerformAutoTransitions() {
        val table = ExTransitionTable.Builder<String, String>()
            .autoTransitionEnabled(true)
            .add("from", null, "intermediate")
            .add("intermediate", null, "to")
            .build()

        val fsm = ExFsm("from", table, autoTransitionEnabled = true)
        fsm.toState("intermediate")

        assertEquals("to", fsm.getState())
    }

    @Test
    fun toStateWithTimeoutShouldWait() {
        val start = System.currentTimeMillis()
        val table = ExTransitionTable.Builder<String, String>()
            .add("from", null, "to", timeout = io.github.ngirchev.fsm.Timeout(1))
            .build()

        val fsm = ExFsm("from", table)
        fsm.toState("to")
        val end = System.currentTimeMillis()

        assertEquals("to", fsm.getState())
        assertTrue(end - start >= 1000)
    }

    @Test
    fun toStateWithActionsShouldExecuteActions() {
        var actionCalled = false
        var postActionCalled = false

        val table = ExTransitionTable.Builder<String, String>()
            .add("from", null, "to",
                action = { actionCalled = true },
                postAction = { postActionCalled = true }
            )
            .build()

        val fsm = ExFsm("from", table)
        fsm.toState("to")

        assertEquals("to", fsm.getState())
        assertTrue(actionCalled)
        assertTrue(postActionCalled)
    }

    @Test
    fun toStateWhenTransitionDoesNotExistShouldThrowException() {
        val table = ExTransitionTable.Builder<String, String>()
            .add("from", null, "to")
            .build()

        val fsm = ExFsm("from", table)

        assertThrows(io.github.ngirchev.fsm.exception.FsmTransitionFailedException::class.java) {
            fsm.toState("nonexistent")
        }
    }

    @Test
    fun toStateWhenFromStateDoesNotMatchShouldThrowException() {
        val table = ExTransitionTable.Builder<String, String>()
            .add("from", null, "to")
            .build()

        val fsm = ExFsm("wrong", table)

        assertThrows(io.github.ngirchev.fsm.exception.FsmException::class.java) {
            fsm.toState("to")
        }
    }

    @Test
    fun toStateWithMultipleAutoTransitionsShouldPerformAll() {
        val table = ExTransitionTable.Builder<String, String>()
            .autoTransitionEnabled(true)
            .add("from", null, "intermediate1")
            .add("intermediate1", null, "intermediate2")
            .add("intermediate2", null, "to")
            .build()

        val fsm = ExFsm("from", table, autoTransitionEnabled = true)
        fsm.toState("intermediate1")

        assertEquals("to", fsm.getState())
    }

    @Test
    fun toStateWithMultipleConditionsShouldCheckAll() {
        var condition1Called = false
        var condition2Called = false

        val table = ExTransitionTable.Builder<String, String>()
            .from("from")
            .to("to")
            .condition { condition1Called = true; true }
            .condition { condition2Called = true; true }
            .end()
            .build()

        val fsm = ExFsm("from", table)
        fsm.toState("to")

        assertEquals("to", fsm.getState())
        assertTrue(condition1Called)
        assertTrue(condition2Called)
    }

    @Test
    fun toStateWithMultipleActionsShouldExecuteAll() {
        var action1Called = false
        var action2Called = false

        val table = ExTransitionTable.Builder<String, String>()
            .from("from")
            .to("to")
            .action { action1Called = true }
            .action { action2Called = true }
            .end()
            .build()

        val fsm = ExFsm("from", table)
        fsm.toState("to")

        assertEquals("to", fsm.getState())
        assertTrue(action1Called)
        assertTrue(action2Called)
    }

    @Test
    fun toStateWithMultiplePostActionsShouldExecuteAll() {
        var postAction1Called = false
        var postAction2Called = false

        val table = ExTransitionTable.Builder<String, String>()
            .from("from")
            .to("to")
            .postAction { postAction1Called = true }
            .postAction { postAction2Called = true }
            .end()
            .build()

        val fsm = ExFsm("from", table)
        fsm.toState("to")

        assertEquals("to", fsm.getState())
        assertTrue(postAction1Called)
        assertTrue(postAction2Called)
    }

    @Test
    fun onEventWithMultipleConditionsShouldCheckAll() {
        var condition1Called = false
        var condition2Called = false

        val table = ExTransitionTable.Builder<String, String>()
            .from("from")
            .onEvent("event")
            .to("to")
            .condition { condition1Called = true; true }
            .condition { condition2Called = true; true }
            .end()
            .build()

        val fsm = ExFsm("from", table)
        fsm.onEvent("event")

        assertEquals("to", fsm.getState())
        assertTrue(condition1Called)
        assertTrue(condition2Called)
    }
}
