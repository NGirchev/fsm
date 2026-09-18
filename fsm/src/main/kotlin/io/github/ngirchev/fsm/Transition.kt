package io.github.ngirchev.fsm

import java.util.concurrent.TimeUnit

interface Transition<STATE> {
    val from: STATE
    val to: To<STATE>
}

interface TypedEvent<out TYPE : Any> {
    val eventType: TYPE
}

internal fun eventTypeOf(event: Any?): Any? {
    return if (event is TypedEvent<*>) event.eventType else event
}

fun interface Action<T> {
    operator fun invoke(context: T)
}

/**
 * Interface for actions that can be identified by ID for serialization
 */
interface IdentifiableAction<T> : Action<T> {
    val id: String?
}

fun interface Guard<T> {
    operator fun invoke(context: T): Boolean
}

/**
 * Interface for guards that can be identified by ID for serialization
 */
interface IdentifiableGuard<T> : Guard<T> {
    val id: String?
}

/**
 * Named action wrapper that provides meaningful toString() representation.
 * Use this when you need a named action for better debugging/logging.
 * Example:
 * ```
 * val action = NamedAction("SendEmail") { ctx -> sendEmail(ctx) }
 * println(action) // prints "SendEmail"
 * ```
 */
open class NamedAction<T>(
    private val name: String,
    private val action: (T) -> Unit
) : Action<T>, IdentifiableAction<T> {
    override fun invoke(context: T) = action(context)
    override fun toString(): String = name
    override val id: String? = name
}

/**
 * Action wrapper with ID for serialization
 */
class IdAction<T>(
    override val id: String,
    private val action: (T) -> Unit
) : Action<T>, IdentifiableAction<T> {
    override fun invoke(context: T) = action(context)
}

/**
 * Named guard/condition wrapper that provides meaningful toString() representation.
 * Use this when you need a named condition for better debugging/logging.
 * Example:
 * ```
 * val guard = NamedGuard("IsAdmin") { ctx -> ctx.user.isAdmin }
 * println(guard) // prints "IsAdmin"
 * ```
 */
open class NamedGuard<T>(
    private val name: String,
    private val guard: (T) -> Boolean
) : Guard<T>, IdentifiableGuard<T> {
    override fun invoke(context: T): Boolean = guard(context)
    override fun toString(): String = name
    override val id: String? = name
}

/**
 * Guard wrapper with ID for serialization
 */
class IdGuard<T>(
    override val id: String,
    private val guard: (T) -> Boolean
) : Guard<T>, IdentifiableGuard<T> {
    override fun invoke(context: T): Boolean = guard(context)
}

/**
 * Target state of a transition together with its declarative content
 * (conditions, actions, postActions, timeout) plus optional per-transition
 * auto-transition settings.
 *
 * Equality and hashCode intentionally exclude auto-transition settings:
 *   - schedulers are typically lambdas, which have no meaningful identity-based equality;
 *   - transition deduplication in builder sets is based on declarative content, so two
 *     transitions with identical state/conditions/actions/postActions/timeout but different
 *     auto-transition settings are treated as duplicates (the second one is rejected with
 *     [io.github.ngirchev.fsm.exception.DuplicateTransitionException]). Use the DSL
 *     `auto().deferWith(...)` on a single transition to attach a scheduler.
 */
class To<STATE>(
    val state: STATE,
    val conditions: List<Guard<in StateContext<STATE>>>,
    val actions: List<Action<in StateContext<STATE>>>,
    val postActions: List<Action<in StateContext<STATE>>>,
    val timeout: Timeout? = null,
) {
    var autoTransitionScheduler: AutoTransitionScheduler<STATE>? = null
        private set

    var autoTransitionEnabled: Boolean = false
        private set

    constructor(
        state: STATE,
        conditions: List<Guard<in StateContext<STATE>>>,
        actions: List<Action<in StateContext<STATE>>>,
        postActions: List<Action<in StateContext<STATE>>>,
        timeout: Timeout? = null,
        autoTransitionScheduler: AutoTransitionScheduler<STATE>? = null,
        autoTransitionEnabled: Boolean = false,
    ) : this(state, conditions, actions, postActions, timeout) {
        this.autoTransitionScheduler = autoTransitionScheduler
        this.autoTransitionEnabled = autoTransitionEnabled || autoTransitionScheduler != null
    }

    // Keep the original five-argument copy JVM API while retaining scheduling metadata.
    fun copy(
        state: STATE = this.state,
        conditions: List<Guard<in StateContext<STATE>>> = this.conditions,
        actions: List<Action<in StateContext<STATE>>> = this.actions,
        postActions: List<Action<in StateContext<STATE>>> = this.postActions,
        timeout: Timeout? = this.timeout,
    ): To<STATE> = To(state, conditions, actions, postActions, timeout, autoTransitionScheduler, autoTransitionEnabled)

    operator fun component1(): STATE = state
    operator fun component2(): List<Guard<in StateContext<STATE>>> = conditions
    operator fun component3(): List<Action<in StateContext<STATE>>> = actions
    operator fun component4(): List<Action<in StateContext<STATE>>> = postActions
    operator fun component5(): Timeout? = timeout

    override fun equals(other: Any?): Boolean =
        this === other || other is To<*> && state == other.state && conditions == other.conditions &&
            actions == other.actions && postActions == other.postActions && timeout == other.timeout

    override fun hashCode(): Int {
        var result = state?.hashCode() ?: 0
        result = 31 * result + conditions.hashCode()
        result = 31 * result + actions.hashCode()
        result = 31 * result + postActions.hashCode()
        return 31 * result + (timeout?.hashCode() ?: 0)
    }

    override fun toString(): String =
        "To(state=$state, conditions=$conditions, actions=$actions, postActions=$postActions, timeout=$timeout)"
}

// Top-level factory function for backwards compatibility - accepts single nullable values
fun <STATE> To(
    state: STATE,
    condition: Guard<in StateContext<STATE>>? = null,
    action: Action<in StateContext<STATE>>? = null,
    postAction: Action<in StateContext<STATE>>? = null,
    timeout: Timeout? = null
): To<STATE> = To(
    state = state,
    conditions = listOfNotNull(condition),
    actions = listOfNotNull(action),
    postActions = listOfNotNull(postAction),
    timeout = timeout,
)

fun <STATE> To(
    state: STATE,
    condition: Guard<in StateContext<STATE>>? = null,
    action: Action<in StateContext<STATE>>? = null,
    postAction: Action<in StateContext<STATE>>? = null,
    timeout: Timeout? = null,
    autoTransitionScheduler: AutoTransitionScheduler<STATE>?,
    autoTransitionEnabled: Boolean = false,
): To<STATE> = To(
    state = state,
    conditions = listOfNotNull(condition),
    actions = listOfNotNull(action),
    postActions = listOfNotNull(postAction),
    timeout = timeout,
    autoTransitionScheduler = autoTransitionScheduler,
    autoTransitionEnabled = autoTransitionEnabled,
)

data class Timeout(
    val value: Long,
    val unit: TimeUnit = TimeUnit.SECONDS
)
