package ru.girchev.fsm.core

import ru.girchev.fsm.FSMContext

interface StateSupport<STATE> {
    fun toState(newState: STATE)
    fun getState(): STATE
    fun toState(transition: SimpleTransition<STATE>)
}

interface EventSupport<EVENT> {
    fun handle(event: EVENT)
}

interface Notifiable<STATE> {
    fun notify(context: FSMContext<STATE>, oldState: STATE, newState: STATE)
}

interface DomainSupport<DOMAIN : FSMContext<STATE>, STATE> {
    fun changeState(domain: DOMAIN, newState: STATE)
}

interface DomainWithEventSupport<DOMAIN : FSMContext<STATE>, STATE, EVENT> {
    fun handle(domain: DOMAIN, event: EVENT)
}
