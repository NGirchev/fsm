package ru.girchev.fsm

import ru.girchev.fsm.core.Action
import ru.girchev.fsm.core.Guard
import ru.girchev.fsm.core.BTransition
import ru.girchev.fsm.core.Timeout

open class Transition<STATE, EVENT>(
    from: STATE,
    val event: EVENT? = null,
    to: STATE,
    condition: Guard<in FSMContext<STATE>>? = null,
    action: Action<in FSMContext<STATE>>? = null,
    timeout: Timeout? = null
) : BTransition<STATE>(from, to, condition, action, timeout) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as Transition<*, *>

        if (event != other.event) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + (event?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        val superToString = super.toString()
        return "Transition" + superToString.substring(
            superToString.indexOf('('),
            superToString.length - 1) +
                ", event=$event)"
    }
}
