package ru.girchev.fsm.impl.extended

import ru.girchev.fsm.EventSupport
import ru.girchev.fsm.StateContext
import ru.girchev.fsm.exception.FsmEventSourcingTransitionFailedException
import ru.girchev.fsm.impl.AbstractFsm

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
