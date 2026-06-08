package io.github.ngirchev.fsm.impl.extended

import io.github.ngirchev.fsm.EventSupport
import io.github.ngirchev.fsm.AutoTransitionScheduler
import io.github.ngirchev.fsm.ImmediateAutoTransitionScheduler
import io.github.ngirchev.fsm.StateContext
import io.github.ngirchev.fsm.exception.FsmEventSourcingTransitionFailedException
import io.github.ngirchev.fsm.impl.AbstractFsm

open class ExFsm<STATE, EVENT> :
    AbstractFsm<STATE, ExTransition<STATE, EVENT>, ExTransitionTable<STATE, EVENT>>, EventSupport<EVENT> {

    constructor(
        state: STATE,
        transitionTable: ExTransitionTable<STATE, EVENT>,
        autoTransitionEnabled: Boolean = true,
    ) : this(state, transitionTable, autoTransitionEnabled, ImmediateAutoTransitionScheduler())

    constructor(
        state: STATE,
        transitionTable: ExTransitionTable<STATE, EVENT>,
        autoTransitionScheduler: AutoTransitionScheduler<STATE>,
    ) : this(state, transitionTable, true, autoTransitionScheduler)

    constructor(
        state: STATE,
        transitionTable: ExTransitionTable<STATE, EVENT>,
        autoTransitionEnabled: Boolean,
        autoTransitionScheduler: AutoTransitionScheduler<STATE>,
    ) : super(
        state,
        transitionTable,
        autoTransitionEnabled,
        autoTransitionScheduler,
    )

    constructor(
        context: StateContext<STATE>,
        transitionTable: ExTransitionTable<STATE, EVENT>,
        autoTransitionEnabled: Boolean = true,
    ) : this(context, transitionTable, autoTransitionEnabled, ImmediateAutoTransitionScheduler())

    constructor(
        context: StateContext<STATE>,
        transitionTable: ExTransitionTable<STATE, EVENT>,
        autoTransitionScheduler: AutoTransitionScheduler<STATE>,
    ) : this(context, transitionTable, true, autoTransitionScheduler)

    constructor(
        context: StateContext<STATE>,
        transitionTable: ExTransitionTable<STATE, EVENT>,
        autoTransitionEnabled: Boolean,
        autoTransitionScheduler: AutoTransitionScheduler<STATE>,
    ) : super(
        context,
        transitionTable,
        autoTransitionEnabled,
        autoTransitionScheduler,
    )

    override fun onEvent(event: EVENT) {
        val transition = transitionTable.getTransitionByEvent(context, event)
            ?: throw FsmEventSourcingTransitionFailedException(context.state.toString(), event.toString())
        toState(ExTransition(transition.from, transition.to, event))
    }
}
