package ru.girchev.fsm

interface StateSupport<STATE> {
    fun toState(newState: STATE)
    fun getState(): STATE
}

interface TransitionSupport<STATE, TRANSITION : Transition<STATE>> {
    fun toState(transition: TRANSITION)
}

interface EventSupport<EVENT> {
    fun onEvent(event: EVENT)
}

interface DomainSupport<DOMAIN : StateContext<STATE>, STATE> {
    /**
     * change state for passed document.
     */
    fun changeState(domain: DOMAIN, newState: STATE)
}

interface Notifiable<STATE> {
    fun notify(context: StateContext<STATE>, oldState: STATE, newState: STATE)
}
