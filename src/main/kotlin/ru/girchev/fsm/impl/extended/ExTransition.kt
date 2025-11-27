package ru.girchev.fsm.impl.extended

import ru.girchev.fsm.To
import ru.girchev.fsm.impl.basic.BTransition

open class ExTransition<STATE, EVENT> : BTransition<STATE> {

    val event: EVENT?

    constructor(from: STATE, to: STATE, onEvent: EVENT? = null) : super(from, To(to)) {
        this.event = onEvent
    }

    constructor(from: STATE, to: To<STATE>, onEvent: EVENT? = null) : super(from, to) {
        this.event = onEvent
    }

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
