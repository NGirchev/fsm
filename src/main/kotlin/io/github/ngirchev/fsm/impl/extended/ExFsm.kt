package io.github.ngirchev.fsm.impl.extended

import io.github.ngirchev.fsm.EventSupport
import io.github.ngirchev.fsm.StateContext
import io.github.ngirchev.fsm.exception.FsmEventSourcingTransitionFailedException
import io.github.ngirchev.fsm.impl.AbstractFsm

open class ExFsm<STATE, EVENT> :
    AbstractFsm<STATE, ExTransition<STATE, EVENT>, ExTransitionTable<STATE, EVENT>>, EventSupport<EVENT> {

    constructor(
        state: STATE,
        transitionTable: ExTransitionTable<STATE, EVENT>,
        autoTransitionEnabled: Boolean = true,
    ) : super(
        state,
        transitionTable,
        autoTransitionEnabled
    )

    constructor(
        context: StateContext<STATE>,
        transitionTable: ExTransitionTable<STATE, EVENT>,
        autoTransitionEnabled: Boolean = true,
    ) : super(
        context,
        transitionTable,
        autoTransitionEnabled
    )

    override fun onEvent(event: EVENT) {
        val transition = transitionTable.getTransitionByEvent(context, event)
            ?: throw FsmEventSourcingTransitionFailedException(context.state.toString(), event.toString())
        toState(transition)
    }
}
