package io.github.ngirchev.fsm.impl

import mu.KLogging
import io.github.ngirchev.fsm.*
import io.github.ngirchev.fsm.exception.FsmException
import io.github.ngirchev.fsm.exception.FsmTransitionFailedException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Finite-state machine
 */
abstract class AbstractFsm<STATE, TRANSITION : AbstractTransition<STATE>, TRANSITION_TABLE : AbstractTransitionTable<STATE, TRANSITION>>(
    context: StateContext<STATE>,
    open val transitionTable: TRANSITION_TABLE,
    autoTransitionEnabled: Boolean? = null
) : StateSupport<STATE>, TransitionSupport<STATE, TRANSITION>, Notifiable<STATE> {

    companion object : KLogging()

    /**
     * Enable auto transitions based on transition table.
     */
    val autoTransitionEnabled: Boolean

    init {
        val overrideAutoTransition = autoTransitionEnabled ?: run {
            transitionTable.autoTransitionEnabled
        }
        this.autoTransitionEnabled = overrideAutoTransition
    }

    constructor(
        state: STATE,
        transitionTable: TRANSITION_TABLE,
        autoTransitionEnabled: Boolean? = null,
    ) : this(
        DefaultStateContext(state),
        transitionTable,
        autoTransitionEnabled
    )

    internal val context: StateContext<STATE> = context

    override fun getState(): STATE {
        return this.context.state
    }

    private val stateChangeListeners = CopyOnWriteArrayList<StateChangeListener<STATE>>()

    fun addStateChangeListener(listener: StateChangeListener<STATE>) {
        stateChangeListeners.add(listener)
    }

    fun removeStateChangeListener(listener: StateChangeListener<STATE>) {
        stateChangeListeners.remove(listener)
    }

    override fun notify(context: StateContext<STATE>, oldState: STATE, newState: STATE) {
        logger.info { "Changed status $oldState -> $newState" }
        stateChangeListeners.forEach { listener ->
            try {
                listener.onStateChanged(context, oldState, newState)
            } catch (e: Exception) {
                logger.error(e) { "Error in state change listener" }
            }
        }
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
        logger.info { "Try to change status $oldState -> $newState" }

        transition.to.actions.forEach { it.invoke(context) }
        context.state = newState
        transition.to.postActions.forEach { it.invoke(context) }
        notify(context, oldState, newState)
    }

    private class DefaultStateContext<STATE>(
        override var state: STATE,
        override var currentTransition: Transition<STATE>? = null
    ) : StateContext<STATE>
}
