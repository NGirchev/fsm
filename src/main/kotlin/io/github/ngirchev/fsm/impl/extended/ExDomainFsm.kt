package io.github.ngirchev.fsm.impl.extended

import mu.KLogging
import io.github.ngirchev.fsm.impl.AbstractDomainFsm
import io.github.ngirchev.fsm.StateContext
import io.github.ngirchev.fsm.StateChangeListener
import java.util.concurrent.CopyOnWriteArrayList

open class ExDomainFsm<DOMAIN : StateContext<STATE>, STATE, EVENT>(
    override val transitionTable: ExTransitionTable<STATE, EVENT>,
    private val autoTransitionEnabled: Boolean? = null
) : AbstractDomainFsm<DOMAIN, STATE, ExTransition<STATE, EVENT>, ExTransitionTable<STATE, EVENT>>(transitionTable), StateChangeListener<STATE> {

    companion object : KLogging()

    private val stateChangeListeners = CopyOnWriteArrayList<StateChangeListener<STATE>>()

    fun addStateChangeListener(listener: StateChangeListener<STATE>) {
        stateChangeListeners.add(listener)
    }

    fun removeStateChangeListener(listener: StateChangeListener<STATE>) {
        stateChangeListeners.remove(listener)
    }

    /**
     * Get AbstractFsm instance for the given domain.
     * This allows accessing AbstractFsm methods through ExDomainFsm.
     * Note: A new instance is created for each domain to avoid state conflicts.
     * The returned instance does not have any listeners attached.
     */
    fun getFsmForDomain(domain: DOMAIN): ExFsm<STATE, EVENT> {
        val overrideAutoTransition = autoTransitionEnabled ?: run {
            transitionTable.autoTransitionEnabled
        }
        return ExFsm(domain, transitionTable, overrideAutoTransition)
    }

    /**
     * handle event for passed document.
     */
    fun handle(domain: DOMAIN, event: EVENT) {
        handleWithListeners(domain) { fsm -> fsm.onEvent(event) }
    }

    override fun changeState(domain: DOMAIN, newState: STATE) {
        handleWithListeners(domain) { fsm -> fsm.toState(newState) }
    }

    fun handleWithListeners(domain: DOMAIN, action: (ExFsm<STATE, EVENT>) -> Unit) {
        val fsm = getFsmForDomain(domain)
        fsm.addStateChangeListener(this)
        try {
            action.invoke(fsm)
        } finally {
            fsm.removeStateChangeListener(this)
        }
    }

    override fun onStateChanged(context: StateContext<STATE>, oldState: STATE, newState: STATE) {
        stateChangeListeners.forEach { listener ->
            listener.onStateChanged(context, oldState, newState)
        }
    }
}
