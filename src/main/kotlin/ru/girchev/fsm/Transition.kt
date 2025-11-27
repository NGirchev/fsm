package ru.girchev.fsm

import java.util.concurrent.TimeUnit

interface Transition<STATE> {
    val from: STATE
    val to: To<STATE>
}

typealias Action<T> = (T) -> Unit
typealias Guard<T> = (T) -> Boolean

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