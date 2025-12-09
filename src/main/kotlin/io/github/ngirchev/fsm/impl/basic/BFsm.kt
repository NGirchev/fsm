package io.github.ngirchev.fsm.impl.basic

import io.github.ngirchev.fsm.StateContext
import io.github.ngirchev.fsm.impl.AbstractFsm

/**
 * Finite-state machine
 */
open class BFsm<STATE> : AbstractFsm<STATE, BTransition<STATE>, BTransitionTable<STATE>> {
    constructor(
        state: STATE,
        transitionTable: BTransitionTable<STATE>,
        autoTransitionEnabled: Boolean? = null
    ) : super(state, transitionTable, autoTransitionEnabled)

    constructor(
        context: StateContext<STATE>,
        transitionTable: BTransitionTable<STATE>,
        autoTransitionEnabled: Boolean? = null
    ) : super(context, transitionTable, autoTransitionEnabled)
}
