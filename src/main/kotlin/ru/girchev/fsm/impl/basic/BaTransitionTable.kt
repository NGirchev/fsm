package ru.girchev.fsm.impl.basic

import ru.girchev.fsm.TransitionTable
import ru.girchev.fsm.exception.DuplicateTransitionException
import ru.girchev.fsm.impl.AbstractTransitionTable

open class BaTransitionTable<STATE>(
    override val transitions: Map<STATE, LinkedHashSet<out BaTransition<STATE>>>
) : AbstractTransitionTable<STATE, BaTransition<STATE>>(transitions) {

    open class Builder<STATE> {

        private val transitions: MutableMap<STATE, LinkedHashSet<BaTransition<STATE>>> = hashMapOf()

        fun add(from: STATE, vararg to: STATE): Builder<STATE> {
            val list: List<BaTransition<STATE>> = to.map { BaTransition(from, it) }
            transitions.putIfAbsent(from, LinkedHashSet())
            for (t in list) {
                if (!transitions[from]!!.add(t)) {
                    throw DuplicateTransitionException(t)
                }
            }
            return this
        }

        fun add(vararg transition: BaTransition<STATE>): Builder<STATE> {
            for (t in transition) {
                transitions.putIfAbsent(t.from, LinkedHashSet())
                if (!transitions[t.from]!!.add(t)) {
                    throw DuplicateTransitionException(t)
                }
            }
            return this
        }

        fun add(from: STATE, vararg to: TransitionTable.To<STATE>): Builder<STATE> {
            for (t in to) {
                transitions.putIfAbsent(from, LinkedHashSet())
                val transition = BaTransition(from, t.to, t.condition, t.action, t.timeout)
                if (!transitions[from]!!.add(transition)) {
                    throw DuplicateTransitionException(transition)
                }
            }
            return this
        }

        open fun build(): BaTransitionTable<STATE> {
            return BaTransitionTable(transitions)
        }
    }
}