package ru.girchev.fsm.core

import ru.girchev.fsm.FSMContext

interface StateSupport<STATE> {
    fun to(newState: STATE)
    fun getState(): STATE
    fun to(transition: BTransition<STATE>)
}

interface EventSupport<EVENT> {
    fun on(event: EVENT)
}

interface Notifiable<STATE> {
    fun notify(context: FSMContext<STATE>, oldState: STATE, newState: STATE)
}

interface DomainSupport<DOMAIN : FSMContext<STATE>, STATE> {
    /**
     * change state for passed document.
     */
    fun changeState(domain: DOMAIN, newState: STATE)
}
