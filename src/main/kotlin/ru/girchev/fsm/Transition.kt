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
    val condition: Guard<in StateContext<STATE>>? = null,
    val action: Action<in StateContext<STATE>>? = null,
    val timeout: Timeout? = null
)

data class Timeout(
    val value: Long,
    val unit: TimeUnit = TimeUnit.SECONDS
)