package io.github.ngirchev.fsm.example.flow

import io.github.ngirchev.fsm.impl.extended.ExFsm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.springframework.context.support.GenericApplicationContext
import java.util.function.Supplier

class FlowCompilerTest {
    private val context = GenericApplicationContext().apply {
        registerBean("allowed", DynamicGuard::class.java, Supplier { DynamicGuard { true } })
        registerBean("mark", DynamicAction::class.java, Supplier { DynamicAction { } })
        refresh()
    }
    private val compiler = FlowCompiler(FlowBehaviorRegistry(context))

    @Test
    fun `compiles valid definition with named Spring behaviors`() {
        val table = compiler.compile(validDefinition())
        val fsm = ExFsm("NEW", table)

        fsm.onEvent("GO")

        assertEquals("DONE", fsm.getState())
    }

    @Test
    fun `rejects missing beans and an unguarded branch before a guarded branch`() {
        val definition = validDefinition().copy(
            transitions = listOf(
                validDefinition().transitions.single().copy(id = "catch-all", guards = emptyList()),
                validDefinition().transitions.single().copy(id = "guarded", guards = listOf("missingGuard")),
            ),
        )

        val issues = compiler.validate(definition)

        assertTrue(issues.any { it.message.contains("Unknown guard bean") })
        assertTrue(issues.any { it.message.contains("hides later transition") })
        assertFailsWith<InvalidFlowDefinitionException> { compiler.compile(definition) }
    }

    @Test
    fun `rejects unknown states events and timeout units`() {
        val transition = validDefinition().transitions.single().copy(
            to = "MISSING",
            trigger = FlowTriggerDefinition("event", "UNKNOWN"),
            timeout = FlowTimeoutDefinition(0, "WEEKS"),
        )

        val issues = compiler.validate(validDefinition().copy(transitions = listOf(transition)))

        assertEquals(4, issues.size)
    }

    @Test
    fun `does not confuse separators in distinct transition fields`() {
        val definition = FlowDefinition(
            schemaVersion = 1,
            initialState = "A|B",
            states = listOf("A|B", "A", "B|C", "C"),
            events = listOf("GO"),
            transitions = listOf(
                FlowTransitionDefinition("first", "A|B", "C", FlowTriggerDefinition("event", "GO")),
                FlowTransitionDefinition("second", "A", "B|C", FlowTriggerDefinition("event", "GO")),
            ),
        )

        assertTrue(compiler.validate(definition).isEmpty())
        compiler.compile(definition)
    }

    private fun validDefinition() = FlowDefinition(
        schemaVersion = 1,
        initialState = "NEW",
        states = listOf("NEW", "DONE"),
        events = listOf("GO"),
        transitions = listOf(
            FlowTransitionDefinition(
                id = "go",
                from = "NEW",
                to = "DONE",
                trigger = FlowTriggerDefinition("event", "GO"),
                guards = listOf("allowed"),
                actions = listOf("mark"),
            ),
        ),
    )
}
