package io.github.ngirchev.fsm.impl.extended

import io.github.ngirchev.fsm.exception.AutoTransitionLimitExceededException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExFsmStartupTest {
    @Test
    fun `startup executes locally enabled transitions and stops before a disabled transition`() {
        val table = ExTransitionTable.Builder<String, String>()
            .from("NEW").to("READY").auto().end()
            .from("READY").to("DONE").end()
            .build()
        val fsm = table.createFsm("NEW")
        var completions = 0
        fsm.addAutoTransitionCompletionListener { completions++ }

        fsm.startAutoTransitions()

        assertEquals("READY", fsm.getState())
        assertEquals(1, completions)
    }

    @Test
    fun `explicit startup executes a complete initial chain and notifies completion`() {
        val table = ExTransitionTable.Builder<String, String>()
            .autoTransitionEnabled(true)
            .maxImmediateAutoTransitions(2)
            .add("NEW", to = "READY")
            .add("READY", to = "DONE")
            .build()
        val fsm = table.createFsm("NEW")
        val states = mutableListOf<String>()
        var completions = 0
        fsm.addStateChangeListener { _, _, state -> states.add(state) }
        fsm.addAutoTransitionCompletionListener { completions++ }

        assertEquals("NEW", fsm.getState())
        fsm.startAutoTransitions()

        assertEquals("DONE", fsm.getState())
        assertEquals(listOf("READY", "DONE"), states)
        assertEquals(1, completions)
    }

    @Test
    fun `startup respects disabled automatic execution`() {
        val table = ExTransitionTable.Builder<String, String>()
            .autoTransitionEnabled(false)
            .add("NEW", to = "DONE")
            .build()
        val fsm = table.createFsm("NEW")

        fsm.startAutoTransitions()

        assertEquals("NEW", fsm.getState())
    }

    @Test
    fun `the runtime limit counts the first automatic transition at startup`() {
        var executions = 0
        val table = ExTransitionTable.Builder<String, String>()
            .autoTransitionEnabled(true)
            .maxImmediateAutoTransitions(2)
            .add("NEW", to = "NEW", action = { executions++ })
            .build()

        val error = assertFailsWith<AutoTransitionLimitExceededException> {
            table.createFsm("NEW").startAutoTransitions()
        }

        assertEquals(2, error.limit)
        assertEquals(2, executions)
    }
}
