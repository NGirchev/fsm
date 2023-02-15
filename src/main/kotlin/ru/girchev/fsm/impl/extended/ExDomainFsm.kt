package ru.girchev.fsm.impl.extended

import mu.KLogging
import ru.girchev.fsm.impl.AbstractDomainFsm
import ru.girchev.fsm.impl.AbstractFsm
import ru.girchev.fsm.StateContext

open class ExDomainFsm<DOMAIN : StateContext<STATE>, STATE, EVENT>(
    override val transitionTable: ExTransitionTable<STATE, EVENT>,
    private val autoTransitionEnabled: Boolean = true
) : AbstractDomainFsm<DOMAIN, STATE, ExTransition<STATE, EVENT>, ExTransitionTable<STATE, EVENT>>(transitionTable) {

    companion object : KLogging()

    /**
     * handle event for passed document.
     */
    fun handle(domain: DOMAIN, event: EVENT) {
        ExFsm(domain, transitionTable, autoTransitionEnabled).onEvent(event)
    }

    override fun changeState(domain: DOMAIN, newState: STATE) {
        object : AbstractFsm<STATE, ExTransition<STATE, EVENT>, ExTransitionTable<STATE, EVENT>>(
            domain,
            transitionTable
        ) {}.toState(newState)
    }
}
