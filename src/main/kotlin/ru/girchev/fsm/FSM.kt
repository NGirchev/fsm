package ru.girchev.fsm

import ru.girchev.fsm.core.EventSupport
import ru.girchev.fsm.core.BaseFSM
import ru.girchev.fsm.core.BTransition
import ru.girchev.fsm.exception.FSMEventSourcingTransitionFailedException

open class FSM<STATE, EVENT> : BaseFSM<STATE>, EventSupport<EVENT> {

    final override val transitionTable: TransitionTable<STATE, EVENT>
    private val autoTransitionEnabled: Boolean

    constructor(
        state: STATE,
        transitionTable: TransitionTable<STATE, EVENT>,
        autoTransitionEnabled: Boolean = true,
    ) : super(
        state,
        transitionTable,
    ) {

        this.transitionTable = transitionTable
        this.autoTransitionEnabled = autoTransitionEnabled
    }

    constructor(
        context: FSMContext<STATE>,
        transitionTable: TransitionTable<STATE, EVENT>,
        autoTransitionEnabled: Boolean = true,
    ) : super(
        context,
        transitionTable,
    ) {
        this.transitionTable = transitionTable
        this.autoTransitionEnabled = autoTransitionEnabled
    }

    override fun on(event: EVENT) {
        transitionTable.getTransition(context, event).also {
            if (it == null) throw FSMEventSourcingTransitionFailedException(context.state.toString(), event.toString())
            changeState(it)
        }
    }

    private fun changeState(transition: BTransition<STATE>) {
        super.to(transition)
        if (autoTransitionEnabled) {
            getAutoTransition()?.also { changeState(it) }
        }
    }

    private fun getAutoTransition(): Transition<STATE, EVENT>? {
        return transitionTable.transitions[context.state]
            ?.firstOrNull {
                it.event == null && it.condition?.invoke(context) != false
            }
    }
}
