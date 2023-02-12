package ru.girchev.fsm.core

import ru.girchev.fsm.FSMContext
import ru.girchev.fsm.exception.FSMException

fun <STATE> BTransitionTable.Builder<STATE>.from(from: STATE): BFromBuilder<STATE> {
    return BFromBuilder(from, this)
}

class BFromBuilder<STATE>(
    private val from: STATE,
    private val rootBuilder: BTransitionTable.Builder<STATE>
) {
    fun to(to: STATE): BToBuilder<STATE> {
        return BToBuilder(from, to, rootBuilder)
    }

    fun toMultiple(): BToMultipleBuilder<STATE> {
        return BToMultipleBuilder(from, rootBuilder)
    }
}

class BToBuilder<STATE>(
    private val from: STATE,
    private val to: STATE,
    private val rootBuilder: BTransitionTable.Builder<STATE>
) {
    private var condition: Guard<in FSMContext<STATE>>? = null
    private var action: Action<in FSMContext<STATE>>? = null

    fun condition(condition: Guard<in FSMContext<STATE>>): BToBuilder<STATE> {
        if (this.condition != null) {
            throw FSMException("Already has condition")
        }
        this.condition = condition
        return this
    }

    fun action(action: Action<in FSMContext<STATE>>): BToBuilder<STATE> {
        if (this.action != null) {
            throw FSMException("Already has condition")
        }
        this.action = action
        return this
    }

    fun end(): BTransitionTable.Builder<STATE> {
        return rootBuilder.add(BTransition(from, to, condition, action))
    }
}

class BToMultipleBuilder<STATE>(private val from: STATE, private val rootBuilder: BTransitionTable.Builder<STATE>) {

    private val transitions: ArrayList<BTransition<STATE>> = ArrayList()
    internal fun addTransition(transition: BTransition<STATE>): BToMultipleBuilder<STATE> {
        transitions.add(transition)
        return this
    }

    fun to(to: STATE): BToMultipleTransitionBuilder<STATE> {
        return BToMultipleTransitionBuilder(from, to, this)
    }

    fun endMultiple(): BTransitionTable.Builder<STATE> {
        for (t in transitions) {
            rootBuilder.add(t)
        }
        return rootBuilder
    }
}

class BToMultipleTransitionBuilder<STATE>(
    private val from: STATE,
    private val to: STATE,
    private val multipleBuilder: BToMultipleBuilder<STATE>
) {
    private var condition: Guard<in FSMContext<STATE>>? = null
    private var action: Action<in FSMContext<STATE>>? = null

    fun condition(condition: Guard<in FSMContext<STATE>>): BToMultipleTransitionBuilder<STATE> {
        if (this.condition != null) {
            throw FSMException("Already has condition")
        }
        this.condition = condition
        return this
    }

    fun action(action: Action<in FSMContext<STATE>>): BToMultipleTransitionBuilder<STATE> {
        if (this.action != null) {
            throw FSMException("Already has action")
        }
        this.action = action
        return this
    }

    fun end(): BToMultipleBuilder<STATE> {
        return multipleBuilder.addTransition(BTransition(from, to, condition, action))
    }
}
