package ru.girchev.fsm.core

import mu.KLogging
import ru.girchev.fsm.FSMContext

open class BDomainFSM<DOMAIN : FSMContext<STATE>, STATE>(
    internal open val transitionTable: BTransitionTable<STATE>
) : DomainSupport<DOMAIN, STATE> {

    companion object : KLogging()

    override fun changeState(domain: DOMAIN, newState: STATE) {
        BaseFSM(domain, transitionTable).to(newState)
    }
}
