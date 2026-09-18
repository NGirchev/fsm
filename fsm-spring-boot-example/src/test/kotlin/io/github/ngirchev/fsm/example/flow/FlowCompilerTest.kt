package io.github.ngirchev.fsm.example.flow

import io.github.ngirchev.fsm.impl.extended.ExFsm
import io.github.ngirchev.fsm.exception.AutoTransitionLimitExceededException
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
    fun `rejects states exceeding PostgreSQL character limit`() {
        listOf("A".repeat(121), "😀".repeat(121)).forEach { state ->
            val definition = FlowDefinition(1, state, states = listOf(state), events = emptyList(), transitions = emptyList())

            assertTrue(compiler.validate(definition).any { it.path == "states" })
            assertFailsWith<InvalidFlowDefinitionException> { compiler.compile(definition) }
        }
    }

    @Test
    fun `accepts state limit in Unicode code points rather than UTF-16 units`() {
        listOf("A".repeat(120), "😀".repeat(120)).forEach { state ->
            val definition = FlowDefinition(1, state, states = listOf(state), events = emptyList(), transitions = emptyList())

            assertTrue(compiler.validate(definition).isEmpty())
            compiler.compile(definition)
        }
    }

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

    @Test
    fun `rejects cyclic auto transitions by default`() {
        val definition = FlowDefinition(
            schemaVersion = 1,
            initialState = "RED",
            autoTransitionEnabled = true,
            states = listOf("RED", "GREEN", "YELLOW"),
            events = emptyList(),
            transitions = listOf(
                FlowTransitionDefinition("red-green", "RED", "GREEN", FlowTriggerDefinition("auto")),
                FlowTransitionDefinition("green-yellow", "GREEN", "YELLOW", FlowTriggerDefinition("auto")),
                FlowTransitionDefinition("yellow-red", "YELLOW", "RED", FlowTriggerDefinition("auto")),
            ),
        )

        val issues = compiler.validate(definition)

        assertTrue(issues.any { it.path == "allowCyclicAutoTransitions" })
        assertFailsWith<InvalidFlowDefinitionException> { compiler.compile(definition) }
    }

    @Test
    fun `allows cyclic auto transitions with explicit opt-in and runtime limit`() {
        val definition = FlowDefinition(
            schemaVersion = 1,
            initialState = "INITIAL",
            autoTransitionEnabled = true,
            allowCyclicAutoTransitions = true,
            maxImmediateAutoTransitions = 2,
            states = listOf("INITIAL", "RED", "GREEN"),
            events = listOf("RUN"),
            transitions = listOf(
                FlowTransitionDefinition("start", "INITIAL", "RED", FlowTriggerDefinition("event", "RUN")),
                FlowTransitionDefinition("red-green", "RED", "GREEN", FlowTriggerDefinition("auto")),
                FlowTransitionDefinition("green-red", "GREEN", "RED", FlowTriggerDefinition("auto")),
            ),
        )

        assertTrue(compiler.validate(definition).isEmpty())
        val table = compiler.compile(definition)
        val error = assertFailsWith<AutoTransitionLimitExceededException> {
            ExFsm("INITIAL", table).onEvent("RUN")
        }
        assertEquals(2, error.limit)
    }

    @Test
    fun `rejects an enabled cyclic flow without a runtime limit`() {
        val definition = cyclicDefinition().copy(
            autoTransitionEnabled = true,
            allowCyclicAutoTransitions = true,
            maxImmediateAutoTransitions = 0,
        )

        val issues = compiler.validate(definition)

        assertTrue(issues.any { it.path == "maxImmediateAutoTransitions" })
        assertFailsWith<InvalidFlowDefinitionException> { compiler.compile(definition) }
    }

    @Test
    fun `allows an explicitly cyclic flow while auto transitions are disabled`() {
        val definition = cyclicDefinition().copy(
            autoTransitionEnabled = false,
            allowCyclicAutoTransitions = true,
            maxImmediateAutoTransitions = 0,
        )

        assertTrue(compiler.validate(definition).isEmpty())
        compiler.compile(definition)
    }

    @Test
    fun `validates a long auto transition chain without overflowing the call stack`() {
        val transitionCount = 10_000
        val states = (0..transitionCount).map { "STATE_$it" }
        val transitions = (0 until transitionCount).map { index ->
            FlowTransitionDefinition(
                id = "transition-$index",
                from = states[index],
                to = states[index + 1],
                trigger = FlowTriggerDefinition("auto"),
            )
        }
        val definition = FlowDefinition(
            schemaVersion = 1,
            initialState = states.first(),
            states = states,
            events = emptyList(),
            transitions = transitions,
        )

        assertTrue(compiler.validate(definition).isEmpty())
    }

    @Test
    fun `rejects a negative immediate auto transition limit`() {
        val issues = compiler.validate(validDefinition().copy(maxImmediateAutoTransitions = -1))

        assertTrue(issues.any { it.path == "maxImmediateAutoTransitions" })
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

    private fun cyclicDefinition() = FlowDefinition(
        schemaVersion = 1,
        initialState = "RED",
        states = listOf("RED", "GREEN"),
        events = emptyList(),
        transitions = listOf(
            FlowTransitionDefinition("red-green", "RED", "GREEN", FlowTriggerDefinition("auto")),
            FlowTransitionDefinition("green-red", "GREEN", "RED", FlowTriggerDefinition("auto")),
        ),
    )
}
