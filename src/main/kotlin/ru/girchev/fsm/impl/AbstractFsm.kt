package ru.girchev.fsm.impl

import mu.KLogging
import ru.girchev.fsm.*
import ru.girchev.fsm.exception.FsmException
import ru.girchev.fsm.exception.FsmTransitionFailedException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

/**
 * Finite-state machine
 */
abstract class AbstractFsm<STATE, TRANSITION : AbstractTransition<STATE>, TRANSITION_TABLE : AbstractTransitionTable<STATE, TRANSITION>> :
    StateSupport<STATE>, TransitionSupport<STATE, TRANSITION>, Notifiable<STATE> {

    companion object : KLogging()

    internal open val transitionTable: TRANSITION_TABLE

    constructor(
        state: STATE,
        transitionTable: TRANSITION_TABLE,
    ) {
        transitionTable.also { this.transitionTable = it }
        this.context = DefaultStateContext(state)
    }

    constructor(
        context: StateContext<STATE>,
        transitionTable: TRANSITION_TABLE,
    ) {
        transitionTable.also { this.transitionTable = it }
        this.context = context
    }

    internal val context: StateContext<STATE>

    override fun getState(): STATE {
        return this.context.state
    }

    override fun notify(context: StateContext<STATE>, oldState: STATE, newState: STATE) {
        logger.info { "Changed status $oldState -> $newState" }
    }

    override fun toState(newState: STATE) {
        val transition = transitionTable.getTransitionByState(context, newState)
        val oldState = context.state
        if (transition == null) throw FsmTransitionFailedException(oldState.toString(), newState.toString())
        toState(transition)
    }

    override fun toState(transition: TRANSITION) {
        val oldState = context.state
        if (transition.from != oldState) throw FsmException(
            "Current state $oldState doesn't fit " +
                    "to change, because transition from=[${transition.from}]"
        )
        transition.to.timeout?.value?.also {
            Thread.sleep(it * 1000)
        }
        transitionExecution(transition)
    }

    private fun transitionExecution(transition: TRANSITION) {
        val oldState = context.state
        val newState = transition.to.state

        transition.to.action?.invoke(context)
        context.state = newState
        notify(context, oldState, newState)
    }

    private fun getExecutorService(): ScheduledExecutorService {
        return Executors.newScheduledThreadPool(1)
    }

    private class DefaultStateContext<STATE>(override var state: STATE) : StateContext<STATE>
}
