package ru.girchev.fsm.impl

import mu.KLogging
import ru.girchev.fsm.FSMContext
import ru.girchev.fsm.Notifiable
import ru.girchev.fsm.StateSupport
import ru.girchev.fsm.impl.basic.BaTransition
import ru.girchev.fsm.exception.FSMException
import ru.girchev.fsm.exception.FSMTransitionFailedException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

/**
 * Finite-state machine
 */
abstract class AbstractFSM<STATE, TRANSITION : BaTransition<STATE>, TRANSITION_TABLE : AbstractTransitionTable<STATE, TRANSITION>> :
    StateSupport<STATE, TRANSITION>, Notifiable<STATE> {

    companion object : KLogging()

    internal open val transitionTable: TRANSITION_TABLE

    constructor(
        state: STATE,
        transitionTable: TRANSITION_TABLE,
    ) {
        transitionTable.also { this.transitionTable = it }
        this.context = DefaultFSMContext(state)
    }

    constructor(
        context: FSMContext<STATE>,
        transitionTable: TRANSITION_TABLE,
    ) {
        transitionTable.also { this.transitionTable = it }
        this.context = context
    }

    internal val context: FSMContext<STATE>

    override fun getState(): STATE {
        return this.context.state
    }

    override fun notify(context: FSMContext<STATE>, oldState: STATE, newState: STATE) {
        logger.info { "Changed status $oldState -> $newState" }
    }

    override fun toState(newState: STATE) {
        val transition = transitionTable.getTransitionByState(context, newState)
        val oldState = context.state
        if (transition == null) throw FSMTransitionFailedException(oldState.toString(), newState.toString())
        toState(transition)
    }

    override fun toState(transition: TRANSITION) {
        val oldState = context.state
        if (transition.from != oldState) throw FSMException(
            "Current state $oldState doesn't fit " +
                    "to change, because transition from=[${transition.from}]"
        )
        transition.timeout?.value?.also {
            Thread.sleep(it * 1000)
        }
        transitionExecution(transition)
    }

    private fun transitionExecution(transition: TRANSITION) {
        val oldState = context.state
        val newState = transition.to

        transition.action?.invoke(context)
        context.state = transition.to
        notify(context, oldState, newState)
    }

    private fun getExecutorService(): ScheduledExecutorService {
        return Executors.newScheduledThreadPool(1)
    }

    private class DefaultFSMContext<STATE>(override var state: STATE) : FSMContext<STATE>
}
