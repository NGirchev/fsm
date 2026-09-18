package io.github.ngirchev.fsm.example.flow

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.ngirchev.fsm.serialization.FsmDto
import io.github.ngirchev.fsm.serialization.ToDto
import io.github.ngirchev.fsm.serialization.TransitionDto
import io.github.ngirchev.fsm.serialization.TimeoutDto

/** Read-only compatibility with definitions already stored by the original example. */
internal fun ObjectMapper.readFlowDefinition(json: String): FlowDefinition {
    val node = readTree(json)
    if (node.has("table")) return treeToValue(node, FlowDefinition::class.java)
    require(node.path("schemaVersion").asInt() == 1) { "Unsupported stored flow format" }
    val transitions = node.path("transitions").map { transition ->
        val trigger = transition.path("trigger")
        val kind = trigger.path("kind").asText()
        require(kind == "event" || kind == "auto") { "Unknown stored trigger kind: $kind" }
        TransitionDto(
            transition.path("from").asText(),
            ToDto(
                transition.path("to").asText(),
                transition.path("guards").map { it.asText() },
                transition.path("actions").map { it.asText() },
                transition.path("postActions").map { it.asText() },
                transition.get("timeout")?.takeUnless { it.isNull }?.let { treeToValue(it, TimeoutDto::class.java) },
            ),
            if (kind == "event") requireNotNull(trigger.get("event")?.textValue()) { "Event trigger requires an event" } else null,
        )
    }
    val states = node.path("states").associate { it.asText() to emptyList<TransitionDto>() }
    return FlowDefinition(node.path("initialState").asText(), FsmDto(
        node.path("autoTransitionEnabled").asBoolean(),
        states + transitions.groupBy { it.from },
        node.path("maxImmediateAutoTransitions").asInt(),
    ))
}
