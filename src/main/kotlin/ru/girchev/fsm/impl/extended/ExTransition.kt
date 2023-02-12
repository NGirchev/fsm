package ru.girchev.fsm.impl.extended

import ru.girchev.fsm.Action
import ru.girchev.fsm.Guard
import ru.girchev.fsm.impl.basic.BaTransition
import ru.girchev.fsm.FSMContext
import ru.girchev.fsm.Timeout

open class ExTransition<STATE, EVENT>(
    from: STATE,
    val event: EVENT? = null,
    to: STATE,
    condition: Guard<in FSMContext<STATE>>? = null,
    action: Action<in FSMContext<STATE>>? = null,
    timeout: Timeout? = null
) : BaTransition<STATE>(from, to, condition, action, timeout) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as ExTransition<*, *>

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
