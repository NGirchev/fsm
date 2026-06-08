package io.github.ngirchev.fsm.impl.extended

import io.github.ngirchev.fsm.AutoTransitionScheduler
import io.github.ngirchev.fsm.ImmediateAutoTransitionScheduler
import io.github.ngirchev.fsm.StateChangeListener
import io.github.ngirchev.fsm.StateContext
import io.github.ngirchev.fsm.impl.AbstractDomainFsm
import java.util.concurrent.CopyOnWriteArrayList

open class ExDomainFsm<DOMAIN : StateContext<STATE>, STATE, EVENT> :
    AbstractDomainFsm<DOMAIN, STATE, ExTransition<STATE, EVENT>, ExTransitionTable<STATE, EVENT>>,
    StateChangeListener<STATE> {
    final override val transitionTable: ExTransitionTable<STATE, EVENT>
    protected val autoTransitionEnabled: Boolean?
    protected val autoTransitionScheduler: AutoTransitionScheduler<STATE>

    constructor(
        transitionTable: ExTransitionTable<STATE, EVENT>,
        autoTransitionEnabled: Boolean? = null,
    ) : this(transitionTable, autoTransitionEnabled, ImmediateAutoTransitionScheduler())

    constructor(
        transitionTable: ExTransitionTable<STATE, EVENT>,
        autoTransitionEnabled: Boolean? = null,
        autoTransitionScheduler: AutoTransitionScheduler<STATE>,
    ) : super(transitionTable) {
        this.transitionTable = transitionTable
        this.autoTransitionEnabled = autoTransitionEnabled
        this.autoTransitionScheduler = autoTransitionScheduler
    }

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
    open fun getFsmForDomain(domain: DOMAIN): ExFsm<STATE, EVENT> {
        val overrideAutoTransition =
            autoTransitionEnabled ?: run {
                transitionTable.autoTransitionEnabled
            }
        return createFsm(domain, overrideAutoTransition)
    }

    protected open fun createFsm(
        domain: DOMAIN,
        autoTransitionEnabled: Boolean,
    ): ExFsm<STATE, EVENT> = ExFsm(domain, transitionTable, autoTransitionEnabled, autoTransitionScheduler)

    /**
     * handle event for passed document.
     */
    fun handle(
        domain: DOMAIN,
        event: EVENT,
    ) {
        handleWithListeners(domain) { fsm -> fsm.onEvent(event) }
    }

    override fun changeState(
        domain: DOMAIN,
        newState: STATE,
    ) {
        handleWithListeners(domain) { fsm -> fsm.toState(newState) }
    }

    fun handleWithListeners(
        domain: DOMAIN,
        action: (ExFsm<STATE, EVENT>) -> Unit,
    ) {
        val fsm = getFsmForDomain(domain)
        lateinit var removeForwardingListener: () -> Unit
        removeForwardingListener = {
            fsm.removeStateChangeListener(this)
            fsm.removeAutoTransitionCompletionListener(removeForwardingListener)
        }
        fsm.addStateChangeListener(this)
        fsm.addAutoTransitionCompletionListener(removeForwardingListener)
        try {
            action.invoke(fsm)
        } catch (e: Exception) {
            removeForwardingListener()
            throw e
        }
    }

    override fun onStateChanged(
        context: StateContext<STATE>,
        oldState: STATE,
        newState: STATE,
    ) {
        stateChangeListeners.forEach { listener ->
            listener.onStateChanged(context, oldState, newState)
        }
    }
}
