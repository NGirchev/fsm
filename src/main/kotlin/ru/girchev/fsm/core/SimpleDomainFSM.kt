package ru.girchev.fsm.core

import mu.KLogging
import ru.girchev.fsm.FSMContext

open class SimpleDomainFSM<DOMAIN : FSMContext<STATE>, STATE>(
    internal open val transitionTable: SimpleTransitionTable<STATE>
) : DomainSupport<DOMAIN, STATE> {

    companion object : KLogging()

    override fun changeState(domain: DOMAIN, newState: STATE) {
        SimpleFSM(domain, transitionTable).toState(newState)
    }
}
