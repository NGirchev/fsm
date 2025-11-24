package ru.girchev.fsm.impl.extended

import mu.KLogging
import ru.girchev.fsm.impl.AbstractDomainFsm
import ru.girchev.fsm.impl.AbstractFsm
import ru.girchev.fsm.StateContext

open class ExDomainFsm<DOMAIN : StateContext<STATE>, STATE, EVENT>(
    override val transitionTable: ExTransitionTable<STATE, EVENT>,
    private val autoTransitionEnabled: Boolean? = null
) : AbstractDomainFsm<DOMAIN, STATE, ExTransition<STATE, EVENT>, ExTransitionTable<STATE, EVENT>>(transitionTable) {

    companion object : KLogging()

    /**
     * handle event for passed document.
     */
    fun handle(domain: DOMAIN, event: EVENT) {
        val overrideAutoTransition = autoTransitionEnabled ?: run {
            transitionTable.autoTransitionEnabled
        }
        ExFsm(domain, transitionTable, overrideAutoTransition).onEvent(event)
    }

    override fun changeState(domain: DOMAIN, newState: STATE) {
        val overrideAutoTransition = autoTransitionEnabled ?: run {
            transitionTable.autoTransitionEnabled
        }
        object : AbstractFsm<STATE, ExTransition<STATE, EVENT>, ExTransitionTable<STATE, EVENT>>(
            domain,
            transitionTable,
            overrideAutoTransition
        ) {}.toState(newState)
    }
}
