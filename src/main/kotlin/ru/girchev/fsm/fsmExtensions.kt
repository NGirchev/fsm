package ru.girchev.fsm

interface StateSupport<STATE, TRANSITION : Transition<STATE>> {
    fun toState(newState: STATE)
    fun getState(): STATE
    fun toState(transition: TRANSITION)
}

interface EventSupport<EVENT> {
    fun onEvent(event: EVENT)
}

interface DomainSupport<DOMAIN : FSMContext<STATE>, STATE> {
    /**
     * change state for passed document.
     */
    fun changeState(domain: DOMAIN, newState: STATE)
}

interface Notifiable<STATE> {
    fun notify(context: FSMContext<STATE>, oldState: STATE, newState: STATE)
}
