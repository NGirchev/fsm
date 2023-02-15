package ru.girchev.fsm.impl

import ru.girchev.fsm.StateContext
import ru.girchev.fsm.TransitionTable

abstract class AbstractTransitionTable<STATE, TRANSITION : AbstractTransition<STATE>>
internal constructor(
    override val transitions: Map<STATE, LinkedHashSet<out TRANSITION>>
) : TransitionTable<STATE, TRANSITION> {

    override fun getTransitionByState(context: StateContext<STATE>, newState: STATE): TRANSITION? {
        return transitions[context.state]?.singleOrNull {
            it.to.state == newState && it.to.condition?.invoke(context) ?: true
        }
    }
}
