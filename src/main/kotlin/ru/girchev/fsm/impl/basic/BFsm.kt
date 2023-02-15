package ru.girchev.fsm.impl.basic

import ru.girchev.fsm.StateContext
import ru.girchev.fsm.impl.AbstractFsm

/**
 * Finite-state machine
 */
open class BFsm<STATE> : AbstractFsm<STATE, BTransition<STATE>, BTransitionTable<STATE>> {
    constructor(
        state: STATE,
        transitionTable: BTransitionTable<STATE>,
    ) : super(state, transitionTable)

    constructor(
        context: StateContext<STATE>,
        transitionTable: BTransitionTable<STATE>,
    ) : super(context, transitionTable)
}
