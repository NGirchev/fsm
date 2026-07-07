package io.github.ngirchev.fsm.serialization

import io.github.ngirchev.fsm.*
import io.github.ngirchev.fsm.exception.FsmException
import io.github.ngirchev.fsm.impl.extended.ExTransition
import io.github.ngirchev.fsm.impl.extended.ExTransitionTable
import java.util.concurrent.TimeUnit

/**
 * Data Transfer Object for FSM serialization
 */
data class FsmDto(
    val autoTransitionEnabled: Boolean,
    val transitions: Map<String, List<TransitionDto>>
)

/**
 * Data Transfer Object for Transition serialization
 * STATE and EVENT are stored as strings for JSON compatibility
 */
data class TransitionDto(
    val from: String,
    val to: ToDto,
    val event: String?
)

/**
 * Data Transfer Object for To serialization
 * STATE is stored as string for JSON compatibility
 */
data class ToDto(
    val state: String,
    val conditions: List<String>,
    val actions: List<String>,
    val postActions: List<String>,
    val timeout: TimeoutDto?,
) {
    var autoTransitionScheduler: String? = null
    var autoTransitionEnabled: Boolean = false

    constructor(
        state: String,
        conditions: List<String>,
        actions: List<String>,
        postActions: List<String>,
        timeout: TimeoutDto?,
        autoTransitionScheduler: String?,
        autoTransitionEnabled: Boolean = false,
    ) : this(state, conditions, actions, postActions, timeout) {
        this.autoTransitionScheduler = autoTransitionScheduler
        this.autoTransitionEnabled = autoTransitionEnabled || autoTransitionScheduler != null
    }
}

/**
 * Data Transfer Object for Timeout serialization
 */
data class TimeoutDto(
    val value: Long,
    val unit: String
)

/**
 * Converts ExTransitionTable to DTO for serialization
 * Note: Map keys are converted to strings for JSON compatibility
 */
fun <STATE, EVENT> ExTransitionTable<STATE, EVENT>.toDto(): FsmDto {
    val transitionsMap = transitions.entries.associate { (state, transitionsSet) ->
        state.toString() to transitionsSet.map { transition ->
            TransitionDto(
                from = transition.from.toString(),
                to = transition.to.toDto(),
                event = transition.event?.toString()
            )
        }
    }
    
    return FsmDto(
        autoTransitionEnabled = autoTransitionEnabled,
        transitions = transitionsMap
    )
}

/**
 * Converts To to DTO for serialization
 * Serializes IdentifiableGuard and IdentifiableAction by their IDs
 */
private fun <STATE> To<STATE>.toDto(): ToDto {
    val conditionIds = conditions.mapNotNull { guard ->
        when (guard) {
            is IdentifiableGuard<*> -> guard.id
            else -> null // Skip non-identifiable guards
        }
    }
    
    val actionIds = actions.mapNotNull { action ->
        when (action) {
            is IdentifiableAction<*> -> action.id
            else -> null // Skip non-identifiable actions
        }
    }
    
    val postActionIds = postActions.mapNotNull { action ->
        when (action) {
            is IdentifiableAction<*> -> action.id
            else -> null // Skip non-identifiable actions
        }
    }
    
    return ToDto(
        state = state.toString(),
        conditions = conditionIds,
        actions = actionIds,
        postActions = postActionIds,
        timeout = timeout?.toDto(),
        autoTransitionScheduler = (autoTransitionScheduler as? IdentifiableAutoTransitionScheduler<*>)?.id,
        autoTransitionEnabled = autoTransitionEnabled,
    )
}

/**
 * Converts Timeout to DTO for serialization
 */
private fun Timeout.toDto(): TimeoutDto {
    return TimeoutDto(
        value = value,
        unit = unit.name
    )
}

/**
 * Factory interface for creating actions by ID
 */
fun interface ActionFactory<STATE> {
    fun createAction(id: String): Action<in StateContext<STATE>>?
}

/**
 * Factory interface for creating guards by ID
 */
fun interface GuardFactory<STATE> {
    fun createGuard(id: String): Guard<in StateContext<STATE>>?
}

/**
 * Factory interface for creating auto-transition schedulers by ID.
 */
fun interface AutoTransitionSchedulerFactory<STATE> {
    fun createScheduler(id: String): AutoTransitionScheduler<STATE>?
}

/**
 * Converts DTO back to To
 * Action and guard factories are optional and best-effort: missing factories or unknown IDs
 * simply drop the corresponding conditions/actions. Scheduler restoration stays strict and uses
 * the overload with [AutoTransitionSchedulerFactory] when scheduler IDs are present in JSON.
 */
fun <STATE> ToDto.toTo(
    stateParser: (String) -> STATE,
    actionFactory: ActionFactory<STATE>? = null,
    guardFactory: GuardFactory<STATE>? = null
): To<STATE> {
    return toTo(stateParser, actionFactory, guardFactory, null)
}

/**
 * Converts DTO back to To
 * Action and guard factories are optional and best-effort: missing factories or unknown IDs
 * simply drop the corresponding conditions/actions. If [autoTransitionSchedulerFactory] is
 * absent or cannot resolve a serialized scheduler ID, deserialization fails fast because that
 * changes auto-transition scheduling semantics.
 */
fun <STATE> ToDto.toTo(
    stateParser: (String) -> STATE,
    actionFactory: ActionFactory<STATE>?,
    guardFactory: GuardFactory<STATE>?,
    autoTransitionSchedulerFactory: AutoTransitionSchedulerFactory<STATE>?,
): To<STATE> {
    val state = stateParser(this.state)

    val conditions = restoreGuards(this.conditions, guardFactory)
    val actions = restoreActions(this.actions, actionFactory)
    val postActions = restoreActions(this.postActions, actionFactory)

    val timeout = this.timeout?.toTimeout()
    val autoTransitionScheduler = this.autoTransitionScheduler?.let { id ->
        val factory = autoTransitionSchedulerFactory
            ?: throw FsmException("Cannot restore auto transition scheduler [$id] without AutoTransitionSchedulerFactory")
        factory.createScheduler(id)
            ?: throw FsmException("Cannot restore auto transition scheduler [$id]")
    }
    
    return To(
        state = state,
        conditions = conditions,
        actions = actions,
        postActions = postActions,
        timeout = timeout,
        autoTransitionScheduler = autoTransitionScheduler,
        autoTransitionEnabled = autoTransitionEnabled,
    )
}

private fun <STATE> restoreGuards(
    guardIds: List<String>,
    guardFactory: GuardFactory<STATE>?,
): List<Guard<in StateContext<STATE>>> {
    val factory = guardFactory ?: return emptyList()
    return guardIds.mapNotNull { id ->
        factory.createGuard(id)
    }
}

private fun <STATE> restoreActions(
    actionIds: List<String>,
    actionFactory: ActionFactory<STATE>?,
): List<Action<in StateContext<STATE>>> {
    val factory = actionFactory ?: return emptyList()
    return actionIds.mapNotNull { id ->
        factory.createAction(id)
    }
}

/**
 * Converts TimeoutDto back to Timeout
 */
private fun TimeoutDto.toTimeout(): Timeout {
    val unit = try {
        TimeUnit.valueOf(unit)
    } catch (e: IllegalArgumentException) {
        TimeUnit.SECONDS // Default fallback
    }
    return Timeout(value, unit)
}

/**
 * Converts DTO back to ExTransitionTable
 * Action and guard factories are optional best-effort helpers. Scheduler restoration is strict:
 * if a transition carries a scheduler ID, [AutoTransitionSchedulerFactory] must be provided and
 * must resolve that ID.
 */
fun <STATE, EVENT> FsmDto.toExTransitionTable(
    stateParser: (String) -> STATE,
    eventParser: (String) -> EVENT,
    actionFactory: ActionFactory<STATE>? = null,
    guardFactory: GuardFactory<STATE>? = null
): ExTransitionTable<STATE, EVENT> {
    return toExTransitionTable(stateParser, eventParser, actionFactory, guardFactory, null)
}

/**
 * Converts DTO back to ExTransitionTable
 * Requires state and event parsers and optional factories for actions/guards/schedulers
 */
fun <STATE, EVENT> FsmDto.toExTransitionTable(
    stateParser: (String) -> STATE,
    eventParser: (String) -> EVENT,
    actionFactory: ActionFactory<STATE>?,
    guardFactory: GuardFactory<STATE>?,
    autoTransitionSchedulerFactory: AutoTransitionSchedulerFactory<STATE>?,
): ExTransitionTable<STATE, EVENT> {
    val builder = ExTransitionTable.Builder<STATE, EVENT>()
    builder.autoTransitionEnabled(autoTransitionEnabled)
    
    transitions.values.flatten().forEach { dto ->
        val fromState = stateParser(dto.from)
        val event = dto.event?.let { eventParser(it) }
        val toDto = dto.to
        val to = toDto.toTo(stateParser, actionFactory, guardFactory, autoTransitionSchedulerFactory)
        val transition = ExTransition(fromState, to, event)
        builder.add(transition)
    }
    
    return builder.build()
}
