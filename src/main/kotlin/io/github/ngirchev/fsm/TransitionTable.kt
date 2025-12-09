package io.github.ngirchev.fsm

interface TransitionTable<STATE, TRANSITION : Transition<STATE>> {
    val transitions: Map<STATE, LinkedHashSet<out TRANSITION>>

    /**
     * get transition by target state.
     */
    fun getTransitionByState(context: StateContext<STATE>, newState: STATE): TRANSITION?

    /**
     * get auto transition for current state.
     * Default implementation doesn't provide auto transitions.
     */
    fun getAutoTransition(context: StateContext<STATE>): TRANSITION? = null

    fun createFsm(initialState: STATE): StateSupport<STATE>
    fun <DOMAIN : StateContext<STATE>> createDomainFsm(): DomainSupport<DOMAIN, STATE>
}