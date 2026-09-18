package io.github.ngirchev.fsm.example.flow

import io.github.ngirchev.fsm.Action
import io.github.ngirchev.fsm.Guard
import io.github.ngirchev.fsm.IdAction
import io.github.ngirchev.fsm.IdGuard
import io.github.ngirchev.fsm.StateContext
import io.github.ngirchev.fsm.impl.extended.ExTransitionTable
import io.github.ngirchev.fsm.serialization.ActionFactory
import io.github.ngirchev.fsm.serialization.GuardFactory
import io.github.ngirchev.fsm.serialization.toExTransitionTable
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/** Application boundaries only; the core converter restores the transition table. */
@Component
class FlowLoader(
    private val actions: Map<String, Action<StateContext<String>>>,
    private val guards: Map<String, Guard<StateContext<String>>>,
) {
    fun load(definition: FlowDefinition): ExTransitionTable<String, String> {
        val issues = validate(definition)
        if (issues.isNotEmpty()) throw InvalidFlowDefinitionException(issues)
        return definition.table.toExTransitionTable(
            stateParser = { it },
            eventParser = { it },
            actionFactory = ActionFactory { id -> IdAction(id, actions.getValue(id)::invoke) },
            guardFactory = GuardFactory { id -> IdGuard(id, guards.getValue(id)::invoke) },
        )
    }

    fun validate(definition: FlowDefinition): List<FlowValidationIssue> {
        val issues = mutableListOf<FlowValidationIssue>()
        fun check(valid: Boolean, path: String, message: String) {
            if (!valid) issues.add(FlowValidationIssue(path, message))
        }
        val table = definition.table
        val transitions = table.transitions.values.flatten()
        val states = table.transitions.keys + transitions.flatMap { listOf(it.from, it.to.state) }
        check(definition.initialState in states, "initialState", "Initial state is not in the table")
        (states + definition.initialState).forEach { state ->
            check(state.isNotBlank() && state.codePointCount(0, state.length) <= 120, "states", "State IDs must contain 1-120 characters")
        }
        check(table.maxImmediateAutoTransitions >= 0, "table.maxImmediateAutoTransitions", "Runtime limit must not be negative")
        check(!table.autoTransitionEnabled || table.maxImmediateAutoTransitions > 0,
            "table.maxImmediateAutoTransitions", "Automatic execution requires a positive runtime limit")
        table.transitions.forEach { (source, group) ->
            check(group.distinct().size == group.size, "table.transitions", "Duplicate transition")
            group.forEach { transition ->
                check(transition.from == source, "table.transitions", "Source state must match its table key")
                check(transition.event?.isNotBlank() != false, "table.transitions", "Event IDs must not be blank")
                transition.to.conditions.forEach { check(it in guards, "conditions", "Unknown guard bean: $it") }
                (transition.to.actions + transition.to.postActions).forEach { check(it in actions, "actions", "Unknown action bean: $it") }
                transition.to.timeout?.let {
                    check(it.value > 0 && runCatching { TimeUnit.valueOf(it.unit) }.isSuccess, "timeout", "Timeout must be positive with a known time unit")
                }
            }
            group.groupBy { it.event }.values.forEach { alternatives ->
                check(alternatives.dropLast(1).none { it.to.conditions.isEmpty() }, "table.transitions", "Unguarded transition hides a later transition")
            }
        }
        return issues
    }
}
