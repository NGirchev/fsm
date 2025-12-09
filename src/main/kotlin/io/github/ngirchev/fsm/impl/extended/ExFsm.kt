package io.github.ngirchev.fsm.impl.extended

import io.github.ngirchev.fsm.EventSupport
import io.github.ngirchev.fsm.StateContext
import io.github.ngirchev.fsm.exception.FsmEventSourcingTransitionFailedException
import io.github.ngirchev.fsm.impl.AbstractFsm

open class ExFsm<STATE, EVENT> :
    AbstractFsm<STATE, ExTransition<STATE, EVENT>, ExTransitionTable<STATE, EVENT>>, EventSupport<EVENT> {

    final override val transitionTable: ExTransitionTable<STATE, EVENT>

    constructor(
        state: STATE,
        transitionTable: ExTransitionTable<STATE, EVENT>,
        autoTransitionEnabled: Boolean = true,
    ) : super(
        state,
        transitionTable,
        autoTransitionEnabled
    ) {
        this.transitionTable = transitionTable
    }

    constructor(
        context: StateContext<STATE>,
        transitionTable: ExTransitionTable<STATE, EVENT>,
        autoTransitionEnabled: Boolean = true,
    ) : super(
        context,
        transitionTable,
        autoTransitionEnabled
    ) {
        this.transitionTable = transitionTable
    }

    override fun onEvent(event: EVENT) {
        val transition = transitionTable.getTransitionByEvent(context, event)
            ?: throw FsmEventSourcingTransitionFailedException(context.state.toString(), event.toString())
        toState(transition)
    }
}
