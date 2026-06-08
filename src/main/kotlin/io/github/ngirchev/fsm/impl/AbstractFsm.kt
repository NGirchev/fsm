package io.github.ngirchev.fsm.impl

import io.github.ngirchev.fsm.*
import io.github.ngirchev.fsm.exception.FsmException
import io.github.ngirchev.fsm.exception.FsmTransitionFailedException
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Finite-state machine
 */
abstract class AbstractFsm<STATE, TRANSITION : AbstractTransition<STATE>, TRANSITION_TABLE : AbstractTransitionTable<STATE, TRANSITION>>(
    context: StateContext<STATE>,
    open val transitionTable: TRANSITION_TABLE,
    autoTransitionEnabled: Boolean? = null,
    protected val autoTransitionScheduler: AutoTransitionScheduler<STATE> = ImmediateAutoTransitionScheduler(),
) : StateSupport<STATE>,
    TransitionSupport<STATE, TRANSITION>,
    Notifiable<STATE> {
    companion object {
        private val logger = LoggerFactory.getLogger(AbstractFsm::class.java)
    }

    /**
     * Enable auto transitions based on transition table.
     */
    val autoTransitionEnabled: Boolean

    init {
        val overrideAutoTransition =
            autoTransitionEnabled ?: run {
                transitionTable.autoTransitionEnabled
            }
        this.autoTransitionEnabled = overrideAutoTransition
    }

    constructor(
        state: STATE,
        transitionTable: TRANSITION_TABLE,
        autoTransitionEnabled: Boolean? = null,
        autoTransitionScheduler: AutoTransitionScheduler<STATE> = ImmediateAutoTransitionScheduler(),
    ) : this(
        DefaultStateContext(state),
        transitionTable,
        autoTransitionEnabled,
        autoTransitionScheduler,
    )

    protected val context: StateContext<STATE> = context

    override fun getState(): STATE = this.context.state

    private val stateChangeListeners = CopyOnWriteArrayList<StateChangeListener<STATE>>()
    private val autoTransitionCompletionListeners = CopyOnWriteArrayList<() -> Unit>()

    open fun addStateChangeListener(listener: StateChangeListener<STATE>) {
        stateChangeListeners.add(listener)
    }

    open fun removeStateChangeListener(listener: StateChangeListener<STATE>) {
        stateChangeListeners.remove(listener)
    }

    open fun addAutoTransitionCompletionListener(listener: () -> Unit) {
        autoTransitionCompletionListeners.add(listener)
    }

    open fun removeAutoTransitionCompletionListener(listener: () -> Unit) {
        autoTransitionCompletionListeners.remove(listener)
    }

    open override fun notify(
        context: StateContext<STATE>,
        oldState: STATE,
        newState: STATE,
    ) {
        logger.info("Changed status {} -> {}", oldState, newState)
        stateChangeListeners.forEach { listener ->
            try {
                listener.onStateChanged(context, oldState, newState)
            } catch (e: Exception) {
                logger.error("Error in state change listener", e)
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
        } else {
            notifyAutoTransitionCompleted()
        }
    }

    protected open fun executeSingleTransition(transition: TRANSITION) {
        val oldState = context.state
        if (transition.from != oldState) {
            throw FsmException(
                "Current state $oldState doesn't fit " +
                    "to change, because transition from=[${transition.from}]",
            )
        }
        transition.to.timeout?.also {
            it.unit.sleep(it.value)
        }
        transitionExecution(transition)
    }

    protected open fun performAutoTransitions() {
        if (autoTransitionScheduler is ImmediateAutoTransitionScheduler) {
            performImmediateAutoTransitions()
            notifyAutoTransitionCompleted()
            return
        }
        performScheduledAutoTransitions()
    }

    protected open fun performImmediateAutoTransitions() {
        while (true) {
            val autoTransition = transitionTable.getAutoTransition(context) ?: return
            executeSingleTransition(autoTransition)
        }
    }

    protected open fun performScheduledAutoTransitions() {
        val autoTransition = transitionTable.getAutoTransition(context) ?: run {
            notifyAutoTransitionCompleted()
            return
        }
        autoTransitionScheduler.schedule(context, autoTransition) {
            executeSingleTransition(autoTransition)
            if (autoTransitionEnabled) {
                performScheduledAutoTransitions()
            } else {
                notifyAutoTransitionCompleted()
            }
        }
    }

    protected open fun notifyAutoTransitionCompleted() {
        autoTransitionCompletionListeners.forEach { listener ->
            try {
                listener.invoke()
            } catch (e: Exception) {
                logger.error("Error in auto transition completion listener", e)
            }
        }
    }

    protected open fun transitionExecution(transition: TRANSITION) {
        val oldState = context.state
        val newState = transition.to.state

        context.currentTransition = transition
        logger.info("Try to change status {} -> {}", oldState, newState)

        transition.to.actions.forEach { it.invoke(context) }
        context.state = newState
        try {
            transition.to.postActions.forEach { it.invoke(context) }
        } finally {
            notify(context, oldState, newState)
        }
    }

    private class DefaultStateContext<STATE>(
        override var state: STATE,
        override var currentTransition: Transition<STATE>? = null,
    ) : StateContext<STATE>
}
