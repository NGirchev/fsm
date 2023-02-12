package ru.girchev.fsm.impl

import ru.girchev.fsm.FSMContext
import ru.girchev.fsm.TransitionTable
import ru.girchev.fsm.impl.basic.BaTransition

abstract class AbstractTransitionTable<STATE, TRANSITION : BaTransition<STATE>>
internal constructor(
    override val transitions: Map<STATE, LinkedHashSet<out TRANSITION>>
) : TransitionTable<STATE, TRANSITION> {

    override fun getTransitionByState(context: FSMContext<STATE>, newState: STATE): TRANSITION? {
        return transitions[context.state]?.singleOrNull {
            it.to == newState && it.condition?.invoke(context) ?: true
        }
    }
}
