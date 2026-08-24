package io.github.ngirchev.fsm.example.flow

import io.github.ngirchev.fsm.IdAction
import io.github.ngirchev.fsm.IdGuard
import io.github.ngirchev.fsm.Action
import io.github.ngirchev.fsm.Guard
import io.github.ngirchev.fsm.StateContext
import io.github.ngirchev.fsm.To
import io.github.ngirchev.fsm.Timeout
import io.github.ngirchev.fsm.impl.extended.ExTransition
import io.github.ngirchev.fsm.impl.extended.ExTransitionTable
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class FlowCompiler(private val behaviors: FlowBehaviorRegistry) {
    fun validate(definition: FlowDefinition): List<FlowValidationIssue> {
        val issues = mutableListOf<FlowValidationIssue>()
        fun issue(path: String, message: String) = issues.add(FlowValidationIssue(path, message))

        if (definition.schemaVersion != 1) issue("schemaVersion", "Only schema version 1 is supported")
        if (definition.states.isEmpty()) issue("states", "At least one state is required")
        if (definition.states.any(String::isBlank)) issue("states", "State IDs must not be blank")
        if (definition.states.distinct().size != definition.states.size) issue("states", "State IDs must be unique")
        if (definition.events.any(String::isBlank)) issue("events", "Event IDs must not be blank")
        if (definition.events.distinct().size != definition.events.size) issue("events", "Event IDs must be unique")
        if (definition.initialState !in definition.states) issue("initialState", "Initial state is not declared")

        val transitionIds = mutableSetOf<String>()
        val signatures = mutableSetOf<String>()
        definition.transitions.forEachIndexed { index, transition ->
            val path = "transitions[$index]"
            if (transition.id.isBlank()) issue("$path.id", "Transition ID must not be blank")
            if (!transitionIds.add(transition.id)) issue("$path.id", "Transition ID must be unique")
            if (transition.from !in definition.states) issue("$path.from", "Source state is not declared")
            if (transition.to !in definition.states) issue("$path.to", "Target state is not declared")
            when (transition.trigger.kind) {
                "event" -> {
                    if (transition.trigger.event == null) issue("$path.trigger.event", "Event trigger requires an event")
                    else if (transition.trigger.event !in definition.events) issue("$path.trigger.event", "Event is not declared")
                }
                "auto" -> if (transition.trigger.event != null) issue("$path.trigger.event", "Auto trigger must not contain an event")
                else -> issue("$path.trigger.kind", "Trigger kind must be event or auto")
            }
            transition.guards.forEach { if (!behaviors.hasGuard(it)) issue("$path.guards", "Unknown guard bean: $it") }
            transition.actions.forEach { if (!behaviors.hasAction(it)) issue("$path.actions", "Unknown action bean: $it") }
            transition.postActions.forEach { if (!behaviors.hasAction(it)) issue("$path.postActions", "Unknown action bean: $it") }
            transition.timeout?.let {
                if (it.value <= 0) issue("$path.timeout.value", "Timeout must be positive")
                if (runCatching { TimeUnit.valueOf(it.unit) }.isFailure) issue("$path.timeout.unit", "Unknown time unit")
            }
            val signature = listOf(
                transition.from, transition.to, transition.trigger.kind, transition.trigger.event,
                transition.guards, transition.actions, transition.postActions, transition.timeout,
            ).joinToString("|")
            if (!signatures.add(signature)) issue(path, "Duplicate transition")
        }

        definition.transitions.groupBy { Triple(it.from, it.trigger.kind, it.trigger.event) }.forEach { (_, group) ->
            val catchAll = group.indexOfFirst { it.guards.isEmpty() }
            if (catchAll >= 0 && catchAll < group.lastIndex) {
                val hiddenId = group[catchAll + 1].id
                issue("transitions", "Unguarded transition ${group[catchAll].id} hides later transition $hiddenId")
            }
        }
        return issues
    }

    fun compile(definition: FlowDefinition): ExTransitionTable<String, String> {
        val issues = validate(definition)
        if (issues.isNotEmpty()) throw InvalidFlowDefinitionException(issues)

        val builder = ExTransitionTable.Builder<String, String>()
            .autoTransitionEnabled(definition.autoTransitionEnabled)
        definition.transitions.forEach { transition ->
            val guards: List<Guard<in StateContext<String>>> = transition.guards.map { name ->
                IdGuard<StateContext<String>>(name) { context -> behaviors.guard(name).test(context) }
            }
            val actions: List<Action<in StateContext<String>>> = transition.actions.map { name ->
                IdAction<StateContext<String>>(name) { context -> behaviors.action(name).execute(context) }
            }
            val postActions: List<Action<in StateContext<String>>> = transition.postActions.map { name ->
                IdAction<StateContext<String>>(name) { context -> behaviors.action(name).execute(context) }
            }
            val timeout = transition.timeout?.let { Timeout(it.value, TimeUnit.valueOf(it.unit)) }
            val to = To(transition.to, guards, actions, postActions, timeout)
            val event = transition.trigger.event.takeIf { transition.trigger.kind == "event" }
            builder.add(ExTransition(transition.from, to, event))
        }
        return builder.build()
    }
}
