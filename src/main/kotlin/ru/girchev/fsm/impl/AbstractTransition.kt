package ru.girchev.fsm.impl

import ru.girchev.fsm.*
import ru.girchev.fsm.impl.basic.BaTransition

abstract class AbstractTransition<STATE> (
    override val from: STATE,
    override val to: STATE,
    override val condition: Guard<in FSMContext<STATE>>? = null,
    override val action: Action<in FSMContext<STATE>>? = null,
    override val timeout: Timeout? = null
) : Transition<STATE> {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BaTransition<*>

        if (from != other.from) return false
        if (to != other.to) return false

        return true
    }

    override fun hashCode(): Int {
        var result = from?.hashCode() ?: 0
        result = 31 * result + (to?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "AbstractTransition(from=$from, to=$to, " +
                "hasCondition=${condition != null}, " +
                "hasAction=${action != null}, " +
                "timeout=$timeout)"
    }
}