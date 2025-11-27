package ru.girchev.fsm.impl.basic

import ru.girchev.fsm.Action
import ru.girchev.fsm.Guard
import ru.girchev.fsm.StateContext
import ru.girchev.fsm.To
import ru.girchev.fsm.exception.DuplicateTransitionException
import ru.girchev.fsm.exception.FsmException
import ru.girchev.fsm.impl.AbstractTransitionTable

open class BTransitionTable<STATE>
internal constructor(
    override val transitions: MutableMap<STATE, LinkedHashSet<BTransition<STATE>>>,
    val autoTransitionEnabled: Boolean
) : AbstractTransitionTable<STATE, BTransition<STATE>>(transitions) {

    class Builder<STATE> {

        private val transitions: MutableMap<STATE, LinkedHashSet<BTransition<STATE>>> = hashMapOf()
        private var autoTransitionEnabled: Boolean = false

        /**
         * Placeholder for symmetry with extended FSM DSL.
         * For basic FSM auto transitions управляются через параметр конструктора `BFsm`.
         */
        fun autoTransitionEnabled(enabled: Boolean): Builder<STATE> {
            this.autoTransitionEnabled = enabled
            return this
        }

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
                val transition = BTransition(
                    from,
                    To(
                        state = t.state,
                        condition = t.condition,
                        action = t.action,
                        postAction = t.postAction,
                        timeout = t.timeout,
                    )
                )
                if (!transitions[from]!!.add(transition)) {
                    throw DuplicateTransitionException(transition)
                }
            }
            return this
        }

        fun from(from: STATE): FromBuilder<STATE> {
            return FromBuilder(from, this)
        }

        fun build(): BTransitionTable<STATE> {
            return BTransitionTable(transitions, autoTransitionEnabled)
        }
    }

    override fun createFsm(initialState: STATE): BFsm<STATE> {
        return BFsm(initialState, this, autoTransitionEnabled)
    }

    override fun <DOMAIN : StateContext<STATE>> createDomainFsm(): BDomainFsm<DOMAIN, STATE> {
        return BDomainFsm(this, autoTransitionEnabled)
    }

    override fun getAutoTransition(context: StateContext<STATE>): BTransition<STATE>? {
        return transitions[context.state]
            ?.firstOrNull {
                it.to.condition?.invoke(context) != false
            }
    }
}

class FromBuilder<STATE>(
    private val from: STATE,
    private val rootBuilder: BTransitionTable.Builder<STATE>
) {
    fun to(to: STATE): ToBuilder<STATE> {
        return ToBuilder(from, to, rootBuilder)
    }

    fun toMultiple(): ToMultipleBuilder<STATE> {
        return ToMultipleBuilder(from, rootBuilder)
    }
}

class ToBuilder<STATE>(
    private val from: STATE,
    private val to: STATE,
    private val rootBuilder: BTransitionTable.Builder<STATE>
) {
    private var condition: Guard<in StateContext<STATE>>? = null
    private var action: Action<in StateContext<STATE>>? = null
    private var postAction: Action<in StateContext<STATE>>? = null

    fun condition(condition: Guard<in StateContext<STATE>>): ToBuilder<STATE> {
        if (this.condition != null) {
            throw FsmException("Already has condition")
        }
        this.condition = condition
        return this
    }

    fun action(action: Action<in StateContext<STATE>>): ToBuilder<STATE> {
        if (this.action != null) {
            throw FsmException("Already has action")
        }
        this.action = action
        return this
    }

    fun postAction(postAction: Action<in StateContext<STATE>>): ToBuilder<STATE> {
        if (this.postAction != null) {
            throw FsmException("Already has postAction")
        }
        this.postAction = postAction
        return this
    }

    fun end(): BTransitionTable.Builder<STATE> {
        return rootBuilder.add(BTransition(from, To(to, condition, action)))
    }
}

class ToMultipleBuilder<STATE>(
    private val from: STATE,
    private val rootBuilder: BTransitionTable.Builder<STATE>
) {

    private val transitions: ArrayList<BTransition<STATE>> = ArrayList()
    internal fun addTransition(transition: BTransition<STATE>): ToMultipleBuilder<STATE> {
        transitions.add(transition)
        return this
    }

    fun to(to: STATE): ToMultipleTransitionBuilder<STATE> {
        return ToMultipleTransitionBuilder(from, to, this)
    }

    fun endMultiple(): BTransitionTable.Builder<STATE> {
        for (t in transitions) {
            rootBuilder.add(t)
        }
        return rootBuilder
    }
}

class ToMultipleTransitionBuilder<STATE>(
    private val from: STATE,
    private val to: STATE,
    private val multipleBuilder: ToMultipleBuilder<STATE>
) {
    private var condition: Guard<in StateContext<STATE>>? = null
    private var action: Action<in StateContext<STATE>>? = null
    private var postAction: Action<in StateContext<STATE>>? = null

    fun condition(condition: Guard<in StateContext<STATE>>): ToMultipleTransitionBuilder<STATE> {
        if (this.condition != null) {
            throw FsmException("Already has condition")
        }
        this.condition = condition
        return this
    }

    fun action(action: Action<in StateContext<STATE>>): ToMultipleTransitionBuilder<STATE> {
        if (this.action != null) {
            throw FsmException("Already has action")
        }
        this.action = action
        return this
    }

    fun postAction(postAction: Action<in StateContext<STATE>>): ToMultipleTransitionBuilder<STATE> {
        if (this.postAction != null) {
            throw FsmException("Already has action")
        }
        this.postAction = postAction
        return this
    }

    fun end(): ToMultipleBuilder<STATE> {
        return multipleBuilder.addTransition(BTransition(from, To(to, condition, action)))
    }
}
