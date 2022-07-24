package ru.girchev.fsm

import mu.KLogging
import ru.girchev.fsm.core.DomainWithEventSupport
import ru.girchev.fsm.core.SimpleDomainFSM

open class DomainFSM<DOMAIN : FSMContext<STATE>, STATE, EVENT>(
    override val transitionTable: TransitionTable<STATE, EVENT>
) : SimpleDomainFSM<DOMAIN, STATE>(transitionTable),
    DomainWithEventSupport<DOMAIN, STATE, EVENT> {

    companion object : KLogging()

    override fun handle(domain: DOMAIN, event: EVENT) {
        FSM(domain, transitionTable).handle(event)
    }
}
