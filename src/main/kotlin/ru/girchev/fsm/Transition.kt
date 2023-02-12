package ru.girchev.fsm

import java.util.concurrent.TimeUnit

interface Transition<STATE> {
    val from: STATE
    val to: STATE
    val condition: Guard<in FSMContext<STATE>>?
    val action: Action<in FSMContext<STATE>>?
    val timeout: Timeout?
}

typealias Action<T> = (T) -> Unit
typealias Guard<T> = (T) -> Boolean

data class Timeout(
    val value: Long,
    val unit: TimeUnit = TimeUnit.SECONDS
)