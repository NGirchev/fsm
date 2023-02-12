package ru.girchev.fsm.impl.extended

import mu.KLogging
import ru.girchev.fsm.impl.AbstractDomainFSM
import ru.girchev.fsm.impl.AbstractFSM
import ru.girchev.fsm.FSMContext

open class ExDomainFSM<DOMAIN : FSMContext<STATE>, STATE, EVENT>(
    override val transitionTable: ExTransitionTable<STATE, EVENT>,
    private val autoTransitionEnabled: Boolean = true
) : AbstractDomainFSM<DOMAIN, STATE, ExTransition<STATE, EVENT>, ExTransitionTable<STATE, EVENT>>(transitionTable) {

    companion object : KLogging()

    /**
     * handle event for passed document.
     */
    fun handle(domain: DOMAIN, event: EVENT) {
        ExFSM(domain, transitionTable, autoTransitionEnabled).onEvent(event)
    }

    override fun changeState(domain: DOMAIN, newState: STATE) {
        object : AbstractFSM<STATE, ExTransition<STATE, EVENT>, ExTransitionTable<STATE, EVENT>>(
            domain,
            transitionTable
        ) {}.toState(newState)
    }
}
