package io.github.ngirchev.fsm.impl.basic

import io.github.ngirchev.fsm.AutoTransitionScheduler
import io.github.ngirchev.fsm.ImmediateAutoTransitionScheduler
import io.github.ngirchev.fsm.StateContext
import io.github.ngirchev.fsm.impl.AbstractFsm

/**
 * Finite-state machine
 */
open class BFsm<STATE> : AbstractFsm<STATE, BTransition<STATE>, BTransitionTable<STATE>> {
    constructor(
        state: STATE,
        transitionTable: BTransitionTable<STATE>,
        autoTransitionEnabled: Boolean? = null,
    ) : this(state, transitionTable, autoTransitionEnabled, ImmediateAutoTransitionScheduler())

    constructor(
        state: STATE,
        transitionTable: BTransitionTable<STATE>,
        autoTransitionEnabled: Boolean? = null,
        autoTransitionScheduler: AutoTransitionScheduler<STATE>,
    ) : super(state, transitionTable, autoTransitionEnabled, autoTransitionScheduler)

    constructor(
        context: StateContext<STATE>,
        transitionTable: BTransitionTable<STATE>,
        autoTransitionEnabled: Boolean? = null,
    ) : this(context, transitionTable, autoTransitionEnabled, ImmediateAutoTransitionScheduler())

    constructor(
        context: StateContext<STATE>,
        transitionTable: BTransitionTable<STATE>,
        autoTransitionEnabled: Boolean? = null,
        autoTransitionScheduler: AutoTransitionScheduler<STATE>,
    ) : super(context, transitionTable, autoTransitionEnabled, autoTransitionScheduler)
}
