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

    open val transitionTable: TRANSITION_TABLE

    /**
     * Enable auto transitions based on transition table.
     */
    private val autoTransitionEnabled: Boolean

    constructor(
        state: STATE,
        transitionTable: TRANSITION_TABLE,
        autoTransitionEnabled: Boolean = false,
    ) {
        transitionTable.also { this.transitionTable = it }
        this.context = DefaultStateContext(state)
        this.autoTransitionEnabled = autoTransitionEnabled
    }

    constructor(
        context: StateContext<STATE>,
        transitionTable: TRANSITION_TABLE,
        autoTransitionEnabled: Boolean = false
    ) {
        transitionTable.also { this.transitionTable = it }
        this.context = context
        this.autoTransitionEnabled = autoTransitionEnabled
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
        executeSingleTransition(transition)
        if (autoTransitionEnabled) {
            performAutoTransitions()
        }
    }

    private fun executeSingleTransition(transition: TRANSITION) {
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

    private fun performAutoTransitions() {
        while (true) {
            val autoTransition = transitionTable.getAutoTransition(context) ?: return
            executeSingleTransition(autoTransition)
        }
    }

    private fun transitionExecution(transition: TRANSITION) {
        val oldState = context.state
        val newState = transition.to.state

        context.currentTransition = transition
        logger.info { "Try to changed status $oldState -> $newState" }

        transition.to.actions.forEach { it.invoke(context) }
        context.state = newState
        transition.to.postActions.forEach { it.invoke(context) }
        notify(context, oldState, newState)
    }

    private fun getExecutorService(): ScheduledExecutorService {
        return Executors.newScheduledThreadPool(1)
    }

    private class DefaultStateContext<STATE>(
        override var state: STATE,
        override var currentTransition: Transition<STATE>? = null
    ) : StateContext<STATE>
}
