package ru.girchev.fsm.impl.extended

import ru.girchev.fsm.*
import ru.girchev.fsm.exception.DuplicateTransitionException
import ru.girchev.fsm.exception.FsmException
import ru.girchev.fsm.impl.AbstractTransitionTable

open class ExTransitionTable<STATE, EVENT>
internal constructor(
    override val transitions: Map<STATE, LinkedHashSet<ExTransition<STATE, EVENT>>>,
    val autoTransitionEnabled: Boolean = true
) : AbstractTransitionTable<STATE, ExTransition<STATE, EVENT>>(transitions) {

    internal fun getTransitionByEvent(context: StateContext<STATE>, event: EVENT): ExTransition<STATE, EVENT>? {
        return transitions[context.state]
            ?.filter { it.event == event }
            ?.firstOrNull { it.to.condition?.invoke(context) ?: true }
    }

    override fun getAutoTransition(context: StateContext<STATE>): ExTransition<STATE, EVENT>? {
        return transitions[context.state]
            ?.firstOrNull {
                it.event == null && it.to.condition?.invoke(context) != false
            }
    }

    class Builder<STATE, EVENT> {

        internal val transitions: MutableMap<STATE, LinkedHashSet<ExTransition<STATE, EVENT>>> = hashMapOf()
        private var autoTransitionEnabled: Boolean = true

        fun autoTransitionEnabled(enabled: Boolean): Builder<STATE, EVENT> {
            this.autoTransitionEnabled = enabled
            return this
        }

        fun add(
            from: STATE,
            event: EVENT? = null,
            to: STATE,
            condition: Guard<in StateContext<STATE>>? = null,
            action: Action<in StateContext<STATE>>? = null,
            postAction: Action<in StateContext<STATE>>? = null,
            timeout: Timeout? = null
        ): Builder<STATE, EVENT> {
            transitions.getOrPut(from) { LinkedHashSet() }
                .also { transitionSet ->
                    val transition = ExTransition(from, To(to, condition, action, postAction, timeout), event)
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

        fun from(from: STATE): FromBuilder<STATE, EVENT> {
            return FromBuilder(from, this)
        }

        fun build(): ExTransitionTable<STATE, EVENT> {
            return ExTransitionTable(transitions, autoTransitionEnabled)
        }
    }

    override fun createFsm(initialState: STATE): ExFsm<STATE, EVENT> {
        return ExFsm(initialState, this, autoTransitionEnabled)
    }

    override fun <DOMAIN : StateContext<STATE>> createDomainFsm(): ExDomainFsm<DOMAIN, STATE, EVENT> {
        return ExDomainFsm(this, autoTransitionEnabled)
    }
}

class FromBuilder<STATE, EVENT>(
    private val from: STATE,
    private val rootBuilder: ExTransitionTable.Builder<STATE, EVENT>
) {
    private var event: EVENT? = null

    fun event(event: EVENT): FromBuilder<STATE, EVENT> {
        if (this.event != null) {
            throw FsmException("Already has event")
        }
        this.event = event
        return this
    }

    fun to(to: STATE): ToBuilder<STATE, EVENT> {
        return ToBuilder(from, to, rootBuilder, event)
    }

    fun toMultiple(): ToMultipleBuilder<STATE, EVENT> {
        return ToMultipleBuilder(from, rootBuilder, event)
    }
}

class ToBuilder<STATE, EVENT>(
    private val from: STATE,
    private val to: STATE,
    private val rootBuilder: ExTransitionTable.Builder<STATE, EVENT>,
    private var event: EVENT? = null
) {
    private var condition: Guard<in StateContext<STATE>>? = null
    private var action: Action<in StateContext<STATE>>? = null
    private var postAction: Action<in StateContext<STATE>>? = null
    private var timeout: Timeout? = null

    fun event(event: EVENT): ToBuilder<STATE, EVENT> {
        if (this.event != null) {
            throw FsmException("Already has event")
        }
        this.event = event
        return this
    }

    fun condition(condition: Guard<in StateContext<STATE>>): ToBuilder<STATE, EVENT> {
        if (this.condition != null) {
            throw FsmException("Already has condition")
        }
        this.condition = condition
        return this
    }

    fun action(action: Action<in StateContext<STATE>>): ToBuilder<STATE, EVENT> {
        if (this.action != null) {
            throw FsmException("Already has action")
        }
        this.action = action
        return this
    }

    fun postAction(postAction: Action<in StateContext<STATE>>): ToBuilder<STATE, EVENT> {
        if (this.postAction != null) {
            throw FsmException("Already has postAction")
        }
        this.postAction = postAction
        return this
    }

    fun timeout(timeout: Timeout): ToBuilder<STATE, EVENT> {
        if (this.timeout != null) {
            throw FsmException("Already has timeout")
        }
        this.timeout = timeout
        return this
    }

    fun end(): ExTransitionTable.Builder<STATE, EVENT> {
        return rootBuilder.add(ExTransition(from, To(to, condition, action, postAction, timeout), event))
    }
}

class ToMultipleBuilder<STATE, EVENT>(
    private val from: STATE,
    private val rootBuilder: ExTransitionTable.Builder<STATE, EVENT>,
    private var event: EVENT? = null
) {

    private val transitions: ArrayList<ExTransition<STATE, EVENT>> = ArrayList()
    internal fun addTransition(transition: ExTransition<STATE, EVENT>): ToMultipleBuilder<STATE, EVENT> {
        transitions.add(transition)
        return this
    }

    fun to(to: STATE): ToMultipleTransitionBuilder<STATE, EVENT> {
        return ToMultipleTransitionBuilder(from, to, this, event)
    }

    fun endMultiple(): ExTransitionTable.Builder<STATE, EVENT> {
        for (t in transitions) {
            rootBuilder.add(t)
        }
        return rootBuilder
    }
}

class ToMultipleTransitionBuilder<STATE, EVENT>(
    private val from: STATE,
    private val to: STATE,
    private val multipleBuilder: ToMultipleBuilder<STATE, EVENT>,
    private var event: EVENT? = null
) {
    private var condition: Guard<in StateContext<STATE>>? = null
    private var action: Action<in StateContext<STATE>>? = null
    private var postAction: Action<in StateContext<STATE>>? = null
    private var timeout: Timeout? = null

    fun event(event: EVENT): ToMultipleTransitionBuilder<STATE, EVENT> {
        if (this.event != null) {
            throw FsmException("Already has event")
        }
        this.event = event
        return this
    }

    fun condition(condition: Guard<in StateContext<STATE>>): ToMultipleTransitionBuilder<STATE, EVENT> {
        if (this.condition != null) {
            throw FsmException("Already has condition")
        }
        this.condition = condition
        return this
    }

    fun action(action: Action<in StateContext<STATE>>): ToMultipleTransitionBuilder<STATE, EVENT> {
        if (this.action != null) {
            throw FsmException("Already has action")
        }
        this.action = action
        return this
    }

    fun postAction(postAction: Action<in StateContext<STATE>>): ToMultipleTransitionBuilder<STATE, EVENT> {
        if (this.postAction != null) {
            throw FsmException("Already has postAction")
        }
        this.postAction = postAction
        return this
    }

    fun timeout(timeout: Timeout): ToMultipleTransitionBuilder<STATE, EVENT> {
        if (this.timeout != null) {
            throw FsmException("Already has timeout")
        }
        this.timeout = timeout
        return this
    }

    fun end(): ToMultipleBuilder<STATE, EVENT> {
        return multipleBuilder.addTransition(ExTransition(from, To(to, condition, action, postAction, timeout), event))
    }
}