package io.github.ngirchev.fsm.impl.basic

import io.github.ngirchev.fsm.AutoTransitionScheduler
import io.github.ngirchev.fsm.ImmediateAutoTransitionScheduler
import io.github.ngirchev.fsm.StateContext
import io.github.ngirchev.fsm.impl.AbstractDomainFsm

open class BDomainFsm<DOMAIN : StateContext<STATE>, STATE> :
    AbstractDomainFsm<DOMAIN, STATE, BTransition<STATE>, BTransitionTable<STATE>> {
    final override val transitionTable: BTransitionTable<STATE>
    protected val autoTransitionEnabled: Boolean?
    protected val autoTransitionScheduler: AutoTransitionScheduler<STATE>

    constructor(
        transitionTable: BTransitionTable<STATE>,
        autoTransitionEnabled: Boolean? = null,
    ) : this(transitionTable, autoTransitionEnabled, ImmediateAutoTransitionScheduler())

    constructor(
        transitionTable: BTransitionTable<STATE>,
        autoTransitionEnabled: Boolean? = null,
        autoTransitionScheduler: AutoTransitionScheduler<STATE>,
    ) : super(
        transitionTable,
    ) {
        this.transitionTable = transitionTable
        this.autoTransitionEnabled = autoTransitionEnabled
        this.autoTransitionScheduler = autoTransitionScheduler
    }

    override fun changeState(
        domain: DOMAIN,
        newState: STATE,
    ) {
        val overrideAutoTransition =
            autoTransitionEnabled ?: run {
                transitionTable.autoTransitionEnabled
            }
        createFsm(domain, overrideAutoTransition).toState(newState)
    }

    protected open fun createFsm(
        domain: DOMAIN,
        autoTransitionEnabled: Boolean,
    ): BFsm<STATE> = BFsm(domain, transitionTable, autoTransitionEnabled, autoTransitionScheduler)
}
