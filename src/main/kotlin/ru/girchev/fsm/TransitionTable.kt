package ru.girchev.fsm

interface TransitionTable<STATE, TRANSITION : Transition<STATE>> {
    val transitions: Map<STATE, LinkedHashSet<out TRANSITION>>

    /**
     * get transition by target state.
     */
    fun getTransitionByState(context: StateContext<STATE>, newState: STATE): TRANSITION?

    fun createFsm(initialState: STATE): StateSupport<STATE>
    fun <DOMAIN : StateContext<STATE>> createDomainFsm(): DomainSupport<DOMAIN, STATE>
}