package io.github.ngirchev.fsm.spring

import com.fasterxml.jackson.databind.ObjectMapper
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
        table.autoTransitionEnabled,
        table.transitions.entries.associate { (state, transitions) ->
            state.toString() to transitions.map { transition ->
                val to = transition.to
                TransitionDto(transition.from.toString(), ToDto(
                    to.state.toString(),
                    to.conditions.map(registry::guardName),
                    to.actions.map(registry::actionName),
                    to.postActions.map(registry::actionName),
                    to.timeout?.let { TimeoutDto(it.value, it.unit.name) },
                ), transition.event?.toString())
            }
        },
        table.maxImmediateAutoTransitions,
    )

    fun <STATE, EVENT> serialize(table: ExTransitionTable<STATE, EVENT>): String =
        objectMapper.writeValueAsString(toDto(table))

    fun <STATE, EVENT> fromDto(
        dto: FsmDto,
        stateParser: (String) -> STATE,
        eventParser: (String) -> EVENT,
    ): ExTransitionTable<STATE, EVENT> = dto.toExTransitionTable(
        stateParser, eventParser,
        ActionFactory { registry.action<STATE>(it) },
        GuardFactory { registry.guard<STATE>(it) },
    )

    fun <STATE, EVENT> deserialize(
        json: String,
        stateParser: (String) -> STATE,
        eventParser: (String) -> EVENT,
    ): ExTransitionTable<STATE, EVENT> = fromDto(
        objectMapper.readValue(json, FsmDto::class.java), stateParser, eventParser,
    )
}
