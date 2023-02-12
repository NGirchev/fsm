package ru.girchev.fsm

import mu.KLogging
import ru.girchev.fsm.core.BDomainFSM

open class DomainFSM<DOMAIN : FSMContext<STATE>, STATE, EVENT>(
    override val transitionTable: TransitionTable<STATE, EVENT>
) : BDomainFSM<DOMAIN, STATE>(transitionTable) {

    companion object : KLogging()

    /**
     * handle event for passed document.
     */
    fun handle(domain: DOMAIN, event: EVENT) {
        FSM(domain, transitionTable).on(event)
    }
}
