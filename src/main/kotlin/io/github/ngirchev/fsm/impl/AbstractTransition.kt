package io.github.ngirchev.fsm.impl

import io.github.ngirchev.fsm.*
import io.github.ngirchev.fsm.impl.basic.BTransition

abstract class AbstractTransition<STATE> (
    override val from: STATE,
    override val to: To<STATE>,
) : Transition<STATE> {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BTransition<*>

        if (from != other.from) return false
        if (to != other.to) return false

        return true
    }

    override fun hashCode(): Int {
        var result = from?.hashCode() ?: 0
        result = 31 * result + to.hashCode()
        return result
    }

    override fun toString(): String {
        return "AbstractTransition(from=$from, to=${to.state}, " +
                "conditionsCount=${to.conditions.size}, " +
                "actionsCount=${to.actions.size}, " +
                "postActionsCount=${to.postActions.size}, " +
                "timeout=${to.timeout})"
    }
}