package ru.girchev.fsm

import ru.girchev.fsm.core.Action
import ru.girchev.fsm.core.Guard
import ru.girchev.fsm.core.Timeout
import ru.girchev.fsm.exception.FSMException

fun <STATE, EVENT> TransitionTable.Builder<STATE, EVENT>.from(from: STATE): FromBuilder<STATE, EVENT> {
    return FromBuilder(from, this)
}

class FromBuilder<STATE, EVENT>(
    private val from: STATE,
    private val rootBuilder: TransitionTable.Builder<STATE, EVENT>
) {
    private var event: EVENT? = null

    fun event(event: EVENT): FromBuilder<STATE, EVENT> {
        if (this.event != null) { throw FSMException("Already has event") }
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
    private val rootBuilder: TransitionTable.Builder<STATE, EVENT>,
    private var event: EVENT? = null
) {
    private var condition: Guard<in FSMContext<STATE>>? = null
    private var action: Action<in FSMContext<STATE>>? = null
    private var timeout: Timeout? = null

    fun event(event: EVENT): ToBuilder<STATE, EVENT> {
        if (this.event != null) { throw FSMException("Already has event") }
        this.event = event
        return this
    }

    fun condition(condition: Guard<in FSMContext<STATE>>): ToBuilder<STATE, EVENT> {
        if (this.condition != null) { throw FSMException("Already has condition") }
        this.condition = condition
        return this
    }

    fun action(action: Action<in FSMContext<STATE>>): ToBuilder<STATE, EVENT> {
        if (this.action != null) { throw FSMException("Already has condition") }
        this.action = action
        return this
    }

    fun timeout(timeout: Timeout): ToBuilder<STATE, EVENT> {
        if (this.timeout != null) { throw FSMException("Already has timeout") }
        this.timeout = timeout
        return this
    }

    fun end(): TransitionTable.Builder<STATE, EVENT> {
        return rootBuilder.add(Transition(from, event, to, condition, action, timeout))
    }
}

class ToMultipleBuilder<STATE, EVENT>(
    private val from: STATE,
    private val rootBuilder: TransitionTable.Builder<STATE, EVENT>,
    private var event: EVENT? = null
) {

    private val transitions: ArrayList<Transition<STATE, EVENT>> = ArrayList()
    internal fun addTransition(transition: Transition<STATE, EVENT>): ToMultipleBuilder<STATE, EVENT> {
        transitions.add(transition)
        return this
    }

    fun to(to: STATE): ToMultipleTransitionBuilder<STATE, EVENT> {
        return ToMultipleTransitionBuilder(from, to, this, event)
    }

    fun endMultiple(): TransitionTable.Builder<STATE, EVENT> {
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
    private var condition: Guard<in FSMContext<STATE>>? = null
    private var action: Action<in FSMContext<STATE>>? = null
    private var timeout: Timeout? = null

    fun event(event: EVENT): ToMultipleTransitionBuilder<STATE, EVENT> {
        if (this.event != null) { throw FSMException("Already has event") }
        this.event = event
        return this
    }

    fun condition(condition: Guard<in FSMContext<STATE>>): ToMultipleTransitionBuilder<STATE, EVENT> {
        if (this.condition != null) { throw FSMException("Already has condition") }
        this.condition = condition
        return this
    }

    fun action(action: Action<in FSMContext<STATE>>): ToMultipleTransitionBuilder<STATE, EVENT> {
        if (this.action != null) { throw FSMException("Already has action") }
        this.action = action
        return this
    }

    fun timeout(timeout: Timeout): ToMultipleTransitionBuilder<STATE, EVENT> {
        if (this.timeout != null) { throw FSMException("Already has timeout") }
        this.timeout = timeout
        return this
    }

    fun end(): ToMultipleBuilder<STATE, EVENT> {
        return multipleBuilder.addTransition(Transition(from, event, to, condition, action, timeout))
    }
}
