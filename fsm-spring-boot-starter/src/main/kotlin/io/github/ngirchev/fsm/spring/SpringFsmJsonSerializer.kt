package io.github.ngirchev.fsm.spring

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.ngirchev.fsm.To
import io.github.ngirchev.fsm.impl.extended.ExTransition
import io.github.ngirchev.fsm.impl.extended.ExTransitionTable
import io.github.ngirchev.fsm.serialization.ActionFactory
import io.github.ngirchev.fsm.serialization.FsmDto
import io.github.ngirchev.fsm.serialization.GuardFactory
import io.github.ngirchev.fsm.serialization.TimeoutDto
import io.github.ngirchev.fsm.serialization.ToDto
import io.github.ngirchev.fsm.serialization.TransitionDto
import io.github.ngirchev.fsm.serialization.toExTransitionTable

/** Serializes references to Spring beans, never the beans themselves. */
class SpringFsmJsonSerializer(
    private val objectMapper: ObjectMapper,
    private val registry: FsmBeanRegistry,
) {
    fun <STATE, EVENT> toDto(table: ExTransitionTable<STATE, EVENT>): FsmDto = FsmDto(
        autoTransitionEnabled = table.autoTransitionEnabled,
        transitions = table.transitions.entries.associate { (state, transitions) ->
            state.toString() to transitions.map { toDto(it) }
        },
        maxImmediateAutoTransitions = table.maxImmediateAutoTransitions,
    )

    fun <STATE, EVENT> serialize(table: ExTransitionTable<STATE, EVENT>): String =
        objectMapper.writeValueAsString(toDto(table))

    fun <STATE, EVENT> fromDto(
        dto: FsmDto,
        stateParser: (String) -> STATE,
        eventParser: (String) -> EVENT,
    ): ExTransitionTable<STATE, EVENT> = dto.toExTransitionTable(
        stateParser = stateParser,
        eventParser = eventParser,
        actionFactory = { name -> registry.action(name) },
        guardFactory = { name -> registry.guard(name) },
    )

    fun <STATE, EVENT> deserialize(
        json: String,
        stateParser: (String) -> STATE,
        eventParser: (String) -> EVENT,
    ): ExTransitionTable<STATE, EVENT> {
        val dto = objectMapper.readValue(json, FsmDto::class.java)
        return fromDto(dto, stateParser, eventParser)
    }

    private fun <STATE, EVENT> toDto(transition: ExTransition<STATE, EVENT>): TransitionDto = TransitionDto(
        from = transition.from.toString(),
        to = toDto(transition.to),
        event = transition.event?.toString(),
    )

    private fun <STATE> toDto(destination: To<STATE>): ToDto = ToDto(
        state = destination.state.toString(),
        conditions = destination.conditions.map(registry::guardName),
        actions = destination.actions.map(registry::actionName),
        postActions = destination.postActions.map(registry::actionName),
        timeout = destination.timeout?.let { timeout ->
            TimeoutDto(value = timeout.value, unit = timeout.unit.name)
        },
        autoTransitionEnabled = destination.autoTransitionEnabled,
    )
}
