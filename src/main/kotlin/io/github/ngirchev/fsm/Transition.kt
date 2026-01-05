package io.github.ngirchev.fsm

import java.util.concurrent.TimeUnit

interface Transition<STATE> {
    val from: STATE
    val to: To<STATE>
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

data class To<STATE>(
    val state: STATE,
    val conditions: List<Guard<in StateContext<STATE>>>,
    val actions: List<Action<in StateContext<STATE>>>,
    val postActions: List<Action<in StateContext<STATE>>>,
    val timeout: Timeout? = null
)

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
    timeout = timeout
)

data class Timeout(
    val value: Long,
    val unit: TimeUnit = TimeUnit.SECONDS
)