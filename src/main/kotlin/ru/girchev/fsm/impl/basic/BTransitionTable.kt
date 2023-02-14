package ru.girchev.fsm.impl.basic

import ru.girchev.fsm.DomainSupport
import ru.girchev.fsm.StateContext
import ru.girchev.fsm.To
import ru.girchev.fsm.exception.DuplicateTransitionException
import ru.girchev.fsm.impl.AbstractTransitionTable

open class BTransitionTable<STATE>
internal constructor(
    override val transitions: Map<STATE, LinkedHashSet<out BTransition<STATE>>>
) : AbstractTransitionTable<STATE, BTransition<STATE>>(transitions) {

    class Builder<STATE> {

        private val transitions: MutableMap<STATE, LinkedHashSet<BTransition<STATE>>> = hashMapOf()

        fun add(from: STATE, vararg to: STATE): Builder<STATE> {
            val list: List<BTransition<STATE>> = to.map { BTransition(from, To(it)) }
            transitions.putIfAbsent(from, LinkedHashSet())
            for (t in list) {
                if (!transitions[from]!!.add(t)) {
                    throw DuplicateTransitionException(t)
                }
            }
            return this
        }

        fun add(vararg transition: BTransition<STATE>): Builder<STATE> {
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
                val transition = BTransition(from, To(t.state, t.condition, t.action, t.timeout))
                if (!transitions[from]!!.add(transition)) {
                    throw DuplicateTransitionException(transition)
                }
            }
            return this
        }

        fun build(): BTransitionTable<STATE> {
            return BTransitionTable(transitions)
        }
    }

    override fun createFsm(initialState: STATE): BFsm<STATE> {
        return BFsm(initialState, this)
    }

    override fun <DOMAIN : StateContext<STATE>> createDomainFsm(): DomainSupport<DOMAIN, STATE> {
        return BDomainFsm(this)
    }
}