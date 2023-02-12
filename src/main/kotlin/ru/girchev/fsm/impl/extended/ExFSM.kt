package ru.girchev.fsm.impl.extended

import ru.girchev.fsm.EventSupport
import ru.girchev.fsm.impl.AbstractFSM
import ru.girchev.fsm.FSMContext
import ru.girchev.fsm.exception.FSMEventSourcingTransitionFailedException

open class ExFSM<STATE, EVENT> :
    AbstractFSM<STATE, ExTransition<STATE, EVENT>,
            ExTransitionTable<STATE, EVENT>>,
    EventSupport<EVENT> {

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
        context: FSMContext<STATE>,
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
            if (it == null) throw FSMEventSourcingTransitionFailedException(context.state.toString(), event.toString())
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
                it.event == null && it.condition?.invoke(context) != false
            }
    }
}
