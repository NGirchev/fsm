package ru.girchev.fsm.core

import ru.girchev.fsm.FSMContext
import ru.girchev.fsm.exception.DuplicateTransitionException

open class SimpleTransitionTable<STATE>
internal constructor(
    internal open val transitions: Map<STATE, LinkedHashSet<out SimpleTransition<STATE>>>
) {

    fun getTransition(context: FSMContext<STATE>, newState: STATE): SimpleTransition<STATE>? {
        return transitions[context.state]?.singleOrNull {
            it.to == newState && it.condition?.invoke(context) ?: true
        }
    }

    class Builder<STATE> {

        private val transitions: MutableMap<STATE, LinkedHashSet<SimpleTransition<STATE>>> = hashMapOf()

        fun add(from: STATE, vararg to: STATE): Builder<STATE> {
            val list: List<SimpleTransition<STATE>> = to.map { SimpleTransition(from, it) }
            transitions.putIfAbsent(from, LinkedHashSet())
            for (t in list) {
                if (!transitions[from]!!.add(t)) {
                    throw DuplicateTransitionException(t)
                }
            }
            return this
        }

        fun add(vararg transition: SimpleTransition<STATE>): Builder<STATE> {
            for (t in transition) {
                transitions.putIfAbsent(t.from, LinkedHashSet())
                if (!transitions[t.from]!!.add(t)) {
                    throw DuplicateTransitionException(t)
                }
            }
            return this
        }

        fun add(from: STATE, vararg to: To<STATE>): Builder<STATE> {
            for (t in to) {
                transitions.putIfAbsent(from, LinkedHashSet())
                val transition = SimpleTransition(from, t.to, t.condition, t.action, t.timeout)
                if (!transitions[from]!!.add(transition)) {
                    throw DuplicateTransitionException(transition)
                }
            }
            return this
        }

        fun build(): SimpleTransitionTable<STATE> {
            return SimpleTransitionTable(transitions)
        }
    }

    data class To<STATE>(
        val to: STATE,
        val condition: Guard<in FSMContext<STATE>>? = null,
        val action: Action<in FSMContext<STATE>>? = null,
        val timeout: Timeout? = null
    )
}
