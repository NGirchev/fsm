package ru.girchev.fsm.impl.basic

import mu.KLogging
import ru.girchev.fsm.FSMContext
import ru.girchev.fsm.impl.AbstractDomainFSM

open class BaDomainFSM<DOMAIN : FSMContext<STATE>, STATE>(
    override val transitionTable: BaTransitionTable<STATE>
) : AbstractDomainFSM<DOMAIN, STATE, BaTransition<STATE>, BaTransitionTable<STATE>>(
    transitionTable
) {

    companion object : KLogging()

    override fun changeState(domain: DOMAIN, newState: STATE) {
        BaFSM(domain, transitionTable).toState(newState)
    }
}
