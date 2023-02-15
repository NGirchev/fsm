package ru.girchev.fsm.impl.extended

import ru.girchev.fsm.*
import ru.girchev.fsm.exception.DuplicateTransitionException
import ru.girchev.fsm.impl.AbstractTransitionTable

open class ExTransitionTable<STATE, EVENT>
internal constructor(
    override val transitions: Map<STATE, LinkedHashSet<ExTransition<STATE, EVENT>>>
) : AbstractTransitionTable<STATE, ExTransition<STATE, EVENT>>(transitions) {

    internal fun getTransitionByEvent(context: StateContext<STATE>, event: EVENT): ExTransition<STATE, EVENT>? {
        return transitions[context.state]
            ?.filter { it.event == event }
            ?.firstOrNull { it.to.condition?.invoke(context) ?: true }
    }

    class Builder<STATE, EVENT> {

        internal val transitions: MutableMap<STATE, LinkedHashSet<ExTransition<STATE, EVENT>>> = hashMapOf()

        fun add(
            from: STATE,
            event: EVENT? = null,
            to: STATE,
            condition: Guard<in StateContext<STATE>>? = null,
            action: Action<in StateContext<STATE>>? = null,
            timeout: Timeout? = null
        ): Builder<STATE, EVENT> {
            transitions.getOrPut(from) { LinkedHashSet() }
                .also { transitionSet ->
                    val transition = ExTransition(from, To(to, condition, action, timeout), event)
                    if (!transitionSet.add(transition)) {
                        throw DuplicateTransitionException(transition)
                    }
                }
            return this
        }

        fun add(vararg transition: ExTransition<STATE, EVENT>): Builder<STATE, EVENT> {
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
                        val transition = ExTransition(from, t, event)
                        if (!transitionSet.add(transition)) {
                            throw DuplicateTransitionException(transition)
                        }
                    }
            }
            return this
        }

        fun build(): ExTransitionTable<STATE, EVENT> {
            return ExTransitionTable(transitions)
        }
    }

    override fun createFsm(initialState: STATE): ExFsm<STATE, EVENT> {
        return ExFsm(initialState, this)
    }

    override fun <DOMAIN : StateContext<STATE>> createDomainFsm(): ExDomainFsm<DOMAIN, STATE, EVENT> {
        return ExDomainFsm(this)
    }
}
