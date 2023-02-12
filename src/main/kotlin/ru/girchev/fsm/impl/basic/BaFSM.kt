package ru.girchev.fsm.impl.basic

import ru.girchev.fsm.FSMContext
import ru.girchev.fsm.impl.AbstractFSM

/**
 * Finite-state machine
 */
open class BaFSM<STATE> : AbstractFSM<STATE, BaTransition<STATE>, BaTransitionTable<STATE>> {
    constructor(
        state: STATE,
        transitionTable: BaTransitionTable<STATE>,
    ) : super(state, transitionTable)

    constructor(
        context: FSMContext<STATE>,
        transitionTable: BaTransitionTable<STATE>,
    ) : super(context, transitionTable)
}
