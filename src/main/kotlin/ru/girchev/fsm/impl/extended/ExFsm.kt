package ru.girchev.fsm.impl.extended

import ru.girchev.fsm.EventSupport
import ru.girchev.fsm.StateContext
import ru.girchev.fsm.exception.FsmEventSourcingTransitionFailedException
import ru.girchev.fsm.impl.AbstractFsm

open class ExFsm<STATE, EVENT> :
    AbstractFsm<STATE, ExTransition<STATE, EVENT>, ExTransitionTable<STATE, EVENT>>, EventSupport<EVENT> {

    final override val transitionTable: ExTransitionTable<STATE, EVENT>
    private val autoTransitionEnabled: Boolean

    constructor(
        state: STATE,
        transitionTable: ExTransitionTable<STATE, EVENT>,
        autoTransitionEnabled: Boolean = true,
    ) : super(
        state,
        transitionTable,
    ) {

        this.transitionTable = transitionTable
        this.autoTransitionEnabled = autoTransitionEnabled
    }

    constructor(
        context: StateContext<STATE>,
        transitionTable: ExTransitionTable<STATE, EVENT>,
        autoTransitionEnabled: Boolean = true,
    ) : super(
        context,
        transitionTable,
    ) {
        this.transitionTable = transitionTable
        this.autoTransitionEnabled = autoTransitionEnabled
    }

    override fun onEvent(event: EVENT) {
        transitionTable.getTransitionByEvent(context, event).also {
            if (it == null) throw FsmEventSourcingTransitionFailedException(context.state.toString(), event.toString())
            changeState(it)
        }
    }

    private fun changeState(transition: ExTransition<STATE, EVENT>) {
        super.toState(transition)
        if (autoTransitionEnabled) {
            getAutoTransition()?.also { changeState(it) }
        }
    }

    private fun getAutoTransition(): ExTransition<STATE, EVENT>? {
        return transitionTable.transitions[context.state]
            ?.firstOrNull {
                it.event == null && it.to.condition?.invoke(context) != false
            }
    }
}
