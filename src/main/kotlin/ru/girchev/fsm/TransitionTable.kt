package ru.girchev.fsm

import kotlin.reflect.KClass

interface TransitionTable<STATE, TRANSITION : Transition<STATE>> {
    val transitions: Map<STATE, LinkedHashSet<out TRANSITION>>

    /**
     * get transition by target state.
     */
    fun getTransitionByState(context: StateContext<STATE>, newState: STATE): TRANSITION?

    fun toFsm(initialState: STATE): StateSupport<STATE>
    fun <DOMAIN : StateContext<STATE>> toDomainFsm(domainClass: KClass<DOMAIN>? = null): DomainSupport<DOMAIN, STATE>
}