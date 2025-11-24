package ru.girchev.fsm.impl.basic

import mu.KLogging
import ru.girchev.fsm.StateContext
import ru.girchev.fsm.impl.AbstractDomainFsm

open class BDomainFsm<DOMAIN : StateContext<STATE>, STATE>(
    override val transitionTable: BTransitionTable<STATE>,
    private val autoTransitionEnabled: Boolean? = null
) : AbstractDomainFsm<DOMAIN, STATE, BTransition<STATE>, BTransitionTable<STATE>>(
    transitionTable
) {

    companion object : KLogging()

    override fun changeState(domain: DOMAIN, newState: STATE) {
        val overrideAutoTransition = autoTransitionEnabled ?: run {
            transitionTable.autoTransitionEnabled
        }
        BFsm(domain, transitionTable, overrideAutoTransition).toState(newState)
    }
}
