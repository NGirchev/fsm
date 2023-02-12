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
open class BaseFSM<STATE> : StateSupport<STATE>, Notifiable<STATE> {

    companion object : KLogging()

    internal open val transitionTable: BTransitionTable<STATE>

    constructor(
        state: STATE,
        transitionTable: BTransitionTable<STATE>,
    ) {
        transitionTable.also { this.transitionTable = it }
        this.context = DefaultFSMContext(state)
    }

    constructor(
        context: FSMContext<STATE>,
        transitionTable: BTransitionTable<STATE>,
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

    override fun to(newState: STATE) {
        val transition = transitionTable.getTransition(context, newState)
        val oldState = context.state
        if (transition == null) throw FSMTransitionFailedException(oldState.toString(), newState.toString())
        to(transition)
    }

    override fun to(transition: BTransition<STATE>) {
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

    private fun transitionExecution(transition: BTransition<STATE>) {
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
