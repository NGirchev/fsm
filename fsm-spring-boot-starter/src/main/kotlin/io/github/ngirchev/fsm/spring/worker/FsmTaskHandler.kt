package io.github.ngirchev.fsm.spring.worker

fun interface FsmTaskHandler<T> {
    fun handle(task: T)
}
