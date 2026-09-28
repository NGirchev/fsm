package io.github.ngirchev.fsm.spring.definition

import io.github.ngirchev.fsm.impl.extended.ExTransitionTable
import io.github.ngirchev.fsm.serialization.TimeoutDto
import io.github.ngirchev.fsm.serialization.TransitionDto
import io.github.ngirchev.fsm.spring.SpringFsmJsonSerializer
import java.util.concurrent.TimeUnit

class FlowLoader(
    private val serializer: SpringFsmJsonSerializer,
) {
    /** Restores the core FSM table and resolves named Spring handlers. */
    fun load(definition: FlowDefinition): ExTransitionTable<String, String> {
        return load(definition) { it }
    }

    /** Restore an application's prepared event type while retaining core validation. */
    fun <EVENT> load(definition: FlowDefinition, eventParser: (String) -> EVENT): ExTransitionTable<String, EVENT> {
        validate(definition)
        return serializer.fromDto(definition.table, { it }, eventParser)
    }

    private fun validate(definition: FlowDefinition) {
        val table = definition.table
        require(table.transitions.containsKey(definition.initialState)) {
            "Initial state is not in the flow table"
        }
        require(table.maxImmediateAutoTransitions >= 0) {
            "Automatic transition limit must not be negative"
        }

        table.transitions.forEach(::validateTransitions)
    }

    private fun validateTransitions(state: String, transitions: List<TransitionDto>) {
        validateState(state)
        require(transitions.toSet().size == transitions.size) { "Duplicate transition" }
        transitions.forEach { transition -> validateTransition(state, transition) }
    }

    private fun validateTransition(state: String, transition: TransitionDto) {
        require(transition.event == null || !transition.to.autoTransitionEnabled) {
            "Only eventless transitions may enable local automatic execution"
        }
        require(state == transition.from) { "Transition source differs from its table key" }
        validateState(transition.to.state)
        transition.to.timeout?.let { timeout ->
            require(validTimeout(timeout)) { "Timeout must be positive with a known time unit" }
        }
    }

    private fun validateState(state: String) {
        require(state.isNotBlank()) { "State must not be blank" }
    }

    private fun validTimeout(timeout: TimeoutDto): Boolean =
        timeout.value > 0 && runCatching { TimeUnit.valueOf(timeout.unit) }.isSuccess
}
