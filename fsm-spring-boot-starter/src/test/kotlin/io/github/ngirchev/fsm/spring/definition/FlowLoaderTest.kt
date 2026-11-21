package io.github.ngirchev.fsm.spring.definition

import tools.jackson.module.kotlin.jacksonObjectMapper
import io.github.ngirchev.fsm.Action
import io.github.ngirchev.fsm.Guard
import io.github.ngirchev.fsm.IdAction
import io.github.ngirchev.fsm.IdGuard
import io.github.ngirchev.fsm.StateContext
import io.github.ngirchev.fsm.exception.AutoTransitionLimitExceededException
import io.github.ngirchev.fsm.impl.extended.ExTransitionTable
import io.github.ngirchev.fsm.serialization.FsmDto
import io.github.ngirchev.fsm.serialization.FsmJsonSerializer
import io.github.ngirchev.fsm.serialization.TimeoutDto
import io.github.ngirchev.fsm.serialization.ToDto
import io.github.ngirchev.fsm.serialization.TransitionDto
import io.github.ngirchev.fsm.spring.FsmBeanRegistry
import io.github.ngirchev.fsm.spring.SpringFsmJsonSerializer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class FlowLoaderTest {
    private var calls = 0
    private val registry = FsmBeanRegistry(
        mapOf("mark" to Action<StateContext<String>> { calls++ }),
        mapOf("allowed" to Guard<StateContext<String>> { true }),
    )
    private val serializer = SpringFsmJsonSerializer(jacksonObjectMapper(), registry)
    private val loader = FlowLoader(serializer)

    @Test
    fun `loads core JSON and invokes named behaviors`() {
        val table = ExTransitionTable.Builder<String, String>()
            .add(
                "NEW",
                "GO",
                "DONE",
                IdGuard("allowed") { true },
                IdAction("mark") { },
                null,
                null,
            ).build()
        val coreSerializer = FsmJsonSerializer()
        val dto = coreSerializer.deserializeDto(coreSerializer.serialize(table))
        val restored = loader.load(FlowDefinition("NEW", dto))
        val fsm = restored.createFsm("NEW")

        fsm.onEvent("GO")

        assertThat(fsm.getState()).isEqualTo("DONE")
        assertThat(calls).isEqualTo(1)
        assertThat(serializer.toDto(restored)).isEqualTo(dto)
    }

    @Test
    fun `unknown handlers are never silently discarded`() {
        listOf(
            ToDto("DONE", listOf("missing"), listOf("mark"), emptyList(), null),
            ToDto("DONE", listOf("allowed"), listOf("missing"), emptyList(), null),
            ToDto("DONE", listOf("allowed"), listOf("mark"), listOf("missing"), null),
        ).forEach { destination ->
            assertThatThrownBy { loader.load(definition(destination)) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
        assertThat(calls).isZero()
    }

    @Test
    fun `accepts long states and rejects a missing initial state`() {
        listOf("A", "😀").forEach { character ->
            assertThatCode { loader.load(definition(target(character.repeat(120)))) }.doesNotThrowAnyException()
            assertThatCode { loader.load(definition(target(character.repeat(121)))) }.doesNotThrowAnyException()
        }
        listOf("", "MISSING").forEach { initial ->
            assertThatThrownBy { loader.load(FlowDefinition(initial, definition(target()).table)) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Test
    fun `rejects invalid timeouts duplicate transitions and mismatched source keys`() {
        listOf(TimeoutDto(0, "SECONDS"), TimeoutDto(1, "WEEKS")).forEach { timeout ->
            assertThatThrownBy { loader.load(definition(target(timeout = timeout))) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
        assertThatCode { loader.load(definition(target(timeout = TimeoutDto(1, "SECONDS")))) }
            .doesNotThrowAnyException()
        val transition = definition(target()).table.transitions.getValue("NEW").single()
        listOf(
            listOf(transition, transition),
            listOf(TransitionDto("OTHER", transition.to, transition.event)),
        ).forEach { transitions ->
            assertThatThrownBy {
                loader.load(FlowDefinition("NEW", FsmDto(false, mapOf("NEW" to transitions))))
            }.isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Test
    fun `rejects blank states`() {
        listOf("", " ", "\u2003").forEach { state ->
            assertThatThrownBy { loader.load(definition(target(state))) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Test
    fun `accepts unlimited automatic transitions and enforces a positive limit`() {
        val table = ExTransitionTable.Builder<String, String>()
            .autoTransitionEnabled(true)
            .maxImmediateAutoTransitions(2)
            .add("NEW", "GO", "RED")
            .add("RED", null, "GREEN")
            .add("GREEN", null, "RED")
            .build()
        val coreSerializer = FsmJsonSerializer()
        val dto = coreSerializer.deserializeDto(coreSerializer.serialize(table))
        assertThatCode { loader.load(FlowDefinition("NEW", FsmDto(true, dto.transitions, 0))) }
            .doesNotThrowAnyException()
        assertThatThrownBy { loader.load(FlowDefinition("NEW", FsmDto(false, dto.transitions, -1))) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { loader.load(FlowDefinition("NEW", dto)).createFsm("NEW").onEvent("GO") }
            .isInstanceOf(AutoTransitionLimitExceededException::class.java)
    }

    @Test
    fun `final states must be known states without outgoing transitions`() {
        val table = FsmDto(false, mapOf("NEW" to listOf(TransitionDto("NEW", target(), "GO"),
            TransitionDto("NEW", target("FAILED"), "FAIL")), "FAILED" to emptyList()))
        val marked = FlowDefinition("NEW", table, finalStates = mapOf(
            "DONE" to FinalStateOutcome.SUCCESS, "FAILED" to FinalStateOutcome.FAILURE))
        assertThatCode { loader.load(marked) }.doesNotThrowAnyException()
        // The starter does not require any final state; an application adds that rule in its activation callback.
        assertThatCode { loader.load(FlowDefinition("NEW", table)) }.doesNotThrowAnyException()
        assertThatThrownBy { loader.load(marked.copy(finalStates = mapOf("MISSING" to FinalStateOutcome.LIMIT))) }
            .isInstanceOf(IllegalArgumentException::class.java).hasMessage("Final state is not in the flow table: MISSING")
        assertThatThrownBy { loader.load(marked.copy(finalStates = mapOf("NEW" to FinalStateOutcome.SUCCESS))) }
            .isInstanceOf(IllegalArgumentException::class.java).hasMessage("Final state must not have outgoing transitions: NEW")
        assertThat(FinalStateOutcome.entries.map { it.name }).containsExactly("SUCCESS", "FAILURE", "LIMIT")
    }

    @Test
    fun `iteration limit is optional and at least one when stated`() {
        val table = definition(target()).table
        listOf(null, 1, 25).forEach { limit ->
            assertThatCode { loader.load(FlowDefinition("NEW", table, execution = FlowExecution(maxIterations = limit))) }
                .doesNotThrowAnyException()
        }
        listOf(0, -3).forEach { limit ->
            assertThatThrownBy { loader.load(FlowDefinition("NEW", table, execution = FlowExecution(maxIterations = limit))) }
                .isInstanceOf(IllegalArgumentException::class.java).hasMessage("Iteration limit must be at least 1")
        }
    }

    @Test
    fun `stored JSON without the new properties still loads and writes them only when stated`() {
        val mapper = jacksonObjectMapper()
        val legacyJson = """{"initialState":"NEW","table":{"autoTransitionEnabled":false,"transitions":{"NEW":[]},""" +
            """"maxImmediateAutoTransitions":0},"execution":{"stateListeners":[],"completionListeners":[]}}"""
        val legacy = mapper.readValue(legacyJson, FlowDefinition::class.java)
        assertThat(legacy.finalStates).isEmpty()
        assertThat(legacy.execution.maxIterations).isNull()
        assertThat(mapper.readTree(mapper.writeValueAsString(legacy))).isEqualTo(mapper.readTree(legacyJson))

        val json = """{"initialState":"NEW","table":{"autoTransitionEnabled":false,"transitions":{"NEW":[],"DONE":[]},""" +
            """"maxImmediateAutoTransitions":50},"execution":{"stateListeners":[],"completionListeners":[],"maxIterations":12},""" +
            """"finalStates":{"DONE":"SUCCESS"}}"""
        val stated = mapper.readValue(json, FlowDefinition::class.java)
        assertThat(stated.finalStates).containsExactlyEntriesOf(mapOf("DONE" to FinalStateOutcome.SUCCESS))
        assertThat(stated.execution.maxIterations).isEqualTo(12)
        assertThat(mapper.readTree(mapper.writeValueAsString(stated))).isEqualTo(mapper.readTree(json))
        assertThatCode { loader.load(stated) }.doesNotThrowAnyException()
    }

    private fun target(state: String = "DONE", timeout: TimeoutDto? = null) =
        ToDto(state, listOf("allowed"), listOf("mark"), emptyList(), timeout)

    @Test
    fun `local auto accepts an unlimited eventless transition`() {
        val local = target().also { it.autoTransitionEnabled = true }
        val eventless = mapOf("NEW" to listOf(TransitionDto("NEW", local, null)))
        assertThat(loader.load(FlowDefinition("NEW", FsmDto(false, eventless, 0))).createFsm("NEW").getState())
            .isEqualTo("NEW")
        val event = mapOf("NEW" to listOf(TransitionDto("NEW", local, "GO")))
        assertThatThrownBy { loader.load(FlowDefinition("NEW", FsmDto(false, event, 1))) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun definition(destination: ToDto) = FlowDefinition(
        "NEW",
        FsmDto(false, mapOf("NEW" to listOf(TransitionDto("NEW", destination, "GO")))),
    )
}
