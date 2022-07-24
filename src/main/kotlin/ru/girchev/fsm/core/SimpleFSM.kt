package ru.girchev.fsm.core

import mu.KLogging
import ru.girchev.fsm.FSMContext
import ru.girchev.fsm.exception.FSMException
import ru.girchev.fsm.exception.FSMTransitionFailedException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

/**
 * Finite-state machine
 */
open class SimpleFSM<STATE> : StateSupport<STATE>, Notifiable<STATE> {

    companion object : KLogging()

    internal open val transitionTable: SimpleTransitionTable<STATE>

    constructor(
        state: STATE,
        transitionTable: SimpleTransitionTable<STATE>,
    ) {
        transitionTable.also { this.transitionTable = it }
        this.context = DefaultFSMContext(state)
    }

    constructor(
        context: FSMContext<STATE>,
        transitionTable: SimpleTransitionTable<STATE>,
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
        val transition = transitionTable.getTransition(context, newState)
        val oldState = context.state
        if (transition == null) throw FSMTransitionFailedException(oldState.toString(), newState.toString())
        toState(transition)
    }

    override fun toState(transition: SimpleTransition<STATE>) {
        val oldState = context.state
        if (transition.from != oldState) throw FSMException(
            "Current state $oldState doesn't fit " +
                    "to change, because transition from=[${transition.from}]"
        )
        if (transition.timeout != null) {
            Thread.sleep(transition.timeout.value * 1000)
        }
        transitionExecution(transition)
    }

    private fun transitionExecution(transition: SimpleTransition<STATE>) {
        val oldState = context.state
        val newState = transition.to

        transition.action?.invoke(context)
        context.state = transition.to
        notify(context, oldState, newState)
    }

    private fun getExecutorService2(): ScheduledExecutorService {
        return Executors.newScheduledThreadPool(1)
    }

    private class DefaultFSMContext<STATE>(override var state: STATE) : FSMContext<STATE>
}
