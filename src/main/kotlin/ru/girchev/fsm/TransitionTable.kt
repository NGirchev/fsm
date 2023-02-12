package ru.girchev.fsm

import ru.girchev.fsm.core.*
import ru.girchev.fsm.exception.DuplicateTransitionException

open class TransitionTable<STATE, EVENT>
internal constructor(
    override val transitions: Map<STATE, LinkedHashSet<Transition<STATE, EVENT>>>
) : BTransitionTable<STATE>(transitions) {

    open fun getTransition(context: FSMContext<STATE>, event: EVENT): Transition<STATE, EVENT>? {
        return transitions[context.state]
            ?.filter { it.event == event }
            ?.firstOrNull { it.condition?.invoke(context) ?: true }
    }

    class Builder<STATE, EVENT> {

        internal val transitions: MutableMap<STATE, LinkedHashSet<Transition<STATE, EVENT>>> = hashMapOf()

        fun add(
            from: STATE,
            event: EVENT? = null,
            to: STATE,
            condition: Guard<in FSMContext<STATE>>? = null,
            action: Action<in FSMContext<STATE>>? = null,
            timeout: Timeout? = null
        ): Builder<STATE, EVENT> {
            transitions.getOrPut(from) { LinkedHashSet() }
                .also { transitionSet ->
                    val transition = Transition(from, event, to, condition, action, timeout)
                    if (!transitionSet.add(transition)) {
                        throw DuplicateTransitionException(transition)
                    }
                }
            return this
        }

        fun add(vararg transition: Transition<STATE, EVENT>): Builder<STATE, EVENT> {
            for (t in transition) {
                transitions.putIfAbsent(t.from, LinkedHashSet())
                if (!transitions[t.from]!!.add(t)) {
                    throw DuplicateTransitionException(t)
                }
            }
            return this
        }

        fun add(from: STATE, event: EVENT? = null, vararg to: To<STATE>): Builder<STATE, EVENT> {
            for (t in to) {
                transitions.getOrPut(from) { LinkedHashSet() }
                    .also { transitionSet ->
                        val transition = Transition(from, event, t.to, t.condition, t.action, t.timeout)
                        if (!transitionSet.add(transition)) {
                            throw DuplicateTransitionException(transition)
                        }
                    }
            }
            return this
        }

        fun build(): TransitionTable<STATE, EVENT> {
            return TransitionTable(transitions)
        }
    }
}
