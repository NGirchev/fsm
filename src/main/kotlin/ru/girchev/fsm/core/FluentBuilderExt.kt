package ru.girchev.fsm.core

import ru.girchev.fsm.FSMContext

fun <STATE> SimpleTransitionTable.Builder<STATE>.from(from: STATE): FromBuilder<STATE> {
    return FromBuilder(from, this)
}

class FromBuilder<STATE>(private val from: STATE, private val rootBuilder: SimpleTransitionTable.Builder<STATE>) {
    fun to(to: STATE): ToBuilder<STATE> {
        return ToBuilder(from, to, rootBuilder)
    }

    fun toSwitch(): ToSwitchBuilder<STATE> {
        return ToSwitchBuilder(from, rootBuilder)
    }
}

class ToBuilder<STATE>(
    private val from: STATE,
    private val to: STATE,
    private val rootBuilder: SimpleTransitionTable.Builder<STATE>
) {
    private var condition: Guard<in FSMContext<STATE>>? = null
    private var action: Action<in FSMContext<STATE>>? = null

    fun withCondition(condition: Guard<in FSMContext<STATE>>): ToBuilder<STATE> {
        this.condition = condition
        return this
    }

    fun withAction(action: Action<in FSMContext<STATE>>): ToBuilder<STATE> {
        this.action = action
        return this
    }

    fun end(): SimpleTransitionTable.Builder<STATE> {
        return rootBuilder.add(SimpleTransition(from, to, condition, action))
    }
}

class ToSwitchBuilder<STATE>(private val from: STATE, private val rootBuilder: SimpleTransitionTable.Builder<STATE>) {

    private val transitions: ArrayList<SimpleTransition<STATE>> = ArrayList()
    internal fun addTransition(transition: SimpleTransition<STATE>): ToSwitchBuilder<STATE> {
        transitions.add(transition)
        return this
    }

    fun case(to: STATE): ToSeveralTransitionBuilder<STATE> {
        return ToSeveralTransitionBuilder(from, to, this)
    }

    fun endSwitch(): SimpleTransitionTable.Builder<STATE> {
        for (t in transitions) {
            rootBuilder.add(t)
        }
        return rootBuilder
    }
}

class ToSeveralTransitionBuilder<STATE>(
    private val from: STATE,
    private val to: STATE,
    private val severalBuilder: ToSwitchBuilder<STATE>
) {
    private var condition: Guard<in FSMContext<STATE>>? = null
    private var action: Action<in FSMContext<STATE>>? = null

    fun withCondition(condition: Guard<in FSMContext<STATE>>): ToSeveralTransitionBuilder<STATE> {
        this.condition = condition
        return this
    }

    fun withAction(action: Action<in FSMContext<STATE>>): ToSeveralTransitionBuilder<STATE> {
        this.action = action
        return this
    }

    fun endCase(): ToSwitchBuilder<STATE> {
        return severalBuilder.addTransition(SimpleTransition(from, to, condition, action))
    }
}
