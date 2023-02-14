package ru.girchev.fsm.impl.extended

import ru.girchev.fsm.*
import ru.girchev.fsm.exception.FsmException

fun <STATE, EVENT> ExTransitionTable.Builder<STATE, EVENT>.from(from: STATE): FromBuilder<STATE, EVENT> {
    return FromBuilder(from, this)
}

class FromBuilder<STATE, EVENT>(
    private val from: STATE,
    private val rootBuilder: ExTransitionTable.Builder<STATE, EVENT>
) {
    private var event: EVENT? = null

    fun event(event: EVENT): FromBuilder<STATE, EVENT> {
        if (this.event != null) { throw FsmException("Already has event") }
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
    private var timeout: Timeout? = null

    fun event(event: EVENT): ToBuilder<STATE, EVENT> {
        if (this.event != null) { throw FsmException("Already has event") }
        this.event = event
        return this
    }

    fun condition(condition: Guard<in StateContext<STATE>>): ToBuilder<STATE, EVENT> {
        if (this.condition != null) { throw FsmException("Already has condition") }
        this.condition = condition
        return this
    }

    fun action(action: Action<in StateContext<STATE>>): ToBuilder<STATE, EVENT> {
        if (this.action != null) { throw FsmException("Already has condition") }
        this.action = action
        return this
    }

    fun timeout(timeout: Timeout): ToBuilder<STATE, EVENT> {
        if (this.timeout != null) { throw FsmException("Already has timeout") }
        this.timeout = timeout
        return this
    }

    fun end(): ExTransitionTable.Builder<STATE, EVENT> {
        return rootBuilder.add(ExTransition(from, To(to, condition, action, timeout), event))
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
    private var timeout: Timeout? = null

    fun event(event: EVENT): ToMultipleTransitionBuilder<STATE, EVENT> {
        if (this.event != null) { throw FsmException("Already has event") }
        this.event = event
        return this
    }

    fun condition(condition: Guard<in StateContext<STATE>>): ToMultipleTransitionBuilder<STATE, EVENT> {
        if (this.condition != null) { throw FsmException("Already has condition") }
        this.condition = condition
        return this
    }

    fun action(action: Action<in StateContext<STATE>>): ToMultipleTransitionBuilder<STATE, EVENT> {
        if (this.action != null) { throw FsmException("Already has action") }
        this.action = action
        return this
    }

    fun timeout(timeout: Timeout): ToMultipleTransitionBuilder<STATE, EVENT> {
        if (this.timeout != null) { throw FsmException("Already has timeout") }
        this.timeout = timeout
        return this
    }

    fun end(): ToMultipleBuilder<STATE, EVENT> {
        return multipleBuilder.addTransition(ExTransition(from, To(to, condition, action, timeout), event))
    }
}
