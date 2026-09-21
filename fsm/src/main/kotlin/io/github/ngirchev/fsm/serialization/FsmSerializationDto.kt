package io.github.ngirchev.fsm.serialization

import io.github.ngirchev.fsm.*
import io.github.ngirchev.fsm.impl.extended.ExTransition
import io.github.ngirchev.fsm.impl.extended.ExTransitionTable
import java.util.concurrent.TimeUnit

/**
 * Data Transfer Object for FSM serialization
 */
data class FsmDto(
    val autoTransitionEnabled: Boolean,
    val transitions: Map<String, List<TransitionDto>>,
    val maxImmediateAutoTransitions: Int = 0,
) {
    constructor(
        autoTransitionEnabled: Boolean,
        transitions: Map<String, List<TransitionDto>>,
    ) : this(autoTransitionEnabled, transitions, 0)

    // Preserve the JVM entry points used by clients compiled before the runtime limit was added.
    fun copy(autoTransitionEnabled: Boolean, transitions: Map<String, List<TransitionDto>>): FsmDto =
        FsmDto(autoTransitionEnabled, transitions, maxImmediateAutoTransitions)

    companion object {
        @JvmStatic
        @JvmName("copy\$default")
        @Suppress("UNUSED_PARAMETER")
        fun copyWithLegacyDefaults(
            original: FsmDto,
            autoTransitionEnabled: Boolean,
            transitions: Map<String, List<TransitionDto>>?,
            mask: Int,
            marker: Any?,
        ): FsmDto = original.copy(
            if (mask and 1 != 0) original.autoTransitionEnabled else autoTransitionEnabled,
            if (mask and 2 != 0) original.transitions else requireNotNull(transitions),
        )
    }
}

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
class ToDto(
    val state: String,
    val conditions: List<String>,
    val actions: List<String>,
    val postActions: List<String>,
    val timeout: TimeoutDto?,
) {
    var autoTransitionEnabled: Boolean = false

    constructor(
        state: String,
        conditions: List<String>,
        actions: List<String>,
        postActions: List<String>,
        timeout: TimeoutDto?,
        autoTransitionEnabled: Boolean,
    ) : this(state, conditions, actions, postActions, timeout) {
        this.autoTransitionEnabled = autoTransitionEnabled
    }

    fun copy(
        state: String = this.state,
        conditions: List<String> = this.conditions,
        actions: List<String> = this.actions,
        postActions: List<String> = this.postActions,
        timeout: TimeoutDto? = this.timeout,
    ): ToDto = ToDto(state, conditions, actions, postActions, timeout).also {
        it.autoTransitionEnabled = autoTransitionEnabled
    }

    operator fun component1(): String = state
    operator fun component2(): List<String> = conditions
    operator fun component3(): List<String> = actions
    operator fun component4(): List<String> = postActions
    operator fun component5(): TimeoutDto? = timeout

    override fun equals(other: Any?): Boolean =
        this === other || other is ToDto && state == other.state && conditions == other.conditions &&
            actions == other.actions && postActions == other.postActions && timeout == other.timeout

    override fun hashCode(): Int {
        var result = state.hashCode()
        result = 31 * result + conditions.hashCode()
        result = 31 * result + actions.hashCode()
        result = 31 * result + postActions.hashCode()
        return 31 * result + (timeout?.hashCode() ?: 0)
    }

    override fun toString(): String =
        "ToDto(state=$state, conditions=$conditions, actions=$actions, postActions=$postActions, timeout=$timeout)"
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
        maxImmediateAutoTransitions = maxImmediateAutoTransitions,
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
 * Converts DTO back to To
 * Action and guard factories are optional and best-effort: missing factories or unknown IDs
 * simply drop the corresponding conditions/actions.
 */
fun <STATE> ToDto.toTo(
    stateParser: (String) -> STATE,
    actionFactory: ActionFactory<STATE>? = null,
    guardFactory: GuardFactory<STATE>? = null
): To<STATE> {
    val state = stateParser(this.state)

    val conditions = restoreGuards(this.conditions, guardFactory)
    val actions = restoreActions(this.actions, actionFactory)
    val postActions = restoreActions(this.postActions, actionFactory)

    val timeout = this.timeout?.toTimeout()

    return To(
        state = state,
        conditions = conditions,
        actions = actions,
        postActions = postActions,
        timeout = timeout,
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
 * Action and guard factories are optional best-effort helpers.
 */
fun <STATE, EVENT> FsmDto.toExTransitionTable(
    stateParser: (String) -> STATE,
    eventParser: (String) -> EVENT,
    actionFactory: ActionFactory<STATE>? = null,
    guardFactory: GuardFactory<STATE>? = null
): ExTransitionTable<STATE, EVENT> {
    val builder = ExTransitionTable.Builder<STATE, EVENT>()
    builder.autoTransitionEnabled(autoTransitionEnabled)
    builder.maxImmediateAutoTransitions(maxImmediateAutoTransitions)
    
    transitions.values.flatten().forEach { dto ->
        val fromState = stateParser(dto.from)
        val event = dto.event?.let { eventParser(it) }
        val toDto = dto.to
        val to = toDto.toTo(stateParser, actionFactory, guardFactory)
        val transition = ExTransition(fromState, to, event)
        builder.add(transition)
    }
    
    return builder.build()
}
