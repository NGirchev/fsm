package ru.girchev.fsm.impl.basic

import ru.girchev.fsm.StateContext
import ru.girchev.fsm.Action
import ru.girchev.fsm.Guard
import ru.girchev.fsm.To
import ru.girchev.fsm.exception.FsmException

fun <STATE> BTransitionTable.Builder<STATE>.from(from: STATE): FromBuilder<STATE> {
    return FromBuilder(from, this)
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

    fun condition(condition: Guard<in StateContext<STATE>>): ToBuilder<STATE> {
        if (this.condition != null) {
            throw FsmException("Already has condition")
        }
        this.condition = condition
        return this
    }

    fun action(action: Action<in StateContext<STATE>>): ToBuilder<STATE> {
        if (this.action != null) {
            throw FsmException("Already has condition")
        }
        this.action = action
        return this
    }

    fun end(): BTransitionTable.Builder<STATE> {
        return rootBuilder.add(BTransition(from, To(to, condition, action)))
    }
}

class ToMultipleBuilder<STATE>(private val from: STATE, private val rootBuilder: BTransitionTable.Builder<STATE>) {

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

    fun end(): ToMultipleBuilder<STATE> {
        return multipleBuilder.addTransition(BTransition(from, To(to, condition, action)))
    }
}
