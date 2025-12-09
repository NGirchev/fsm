package io.github.ngirchev.fsm.impl.extended

import mu.KLogging
import io.github.ngirchev.fsm.impl.AbstractDomainFsm
import io.github.ngirchev.fsm.impl.AbstractFsm
import io.github.ngirchev.fsm.StateContext

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
