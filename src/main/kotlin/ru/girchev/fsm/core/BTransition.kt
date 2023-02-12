package ru.girchev.fsm.core

import ru.girchev.fsm.FSMContext
import java.util.concurrent.TimeUnit

open class BTransition<STATE>(
    val from: STATE,
    val to: STATE,
    val condition: Guard<in FSMContext<STATE>>? = null,
    val action: Action<in FSMContext<STATE>>? = null,
    val timeout: Timeout? = null
) {
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
        result = 31 * result + (to?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "BaseTransition(from=$from, to=$to, " +
                "hasCondition=${condition != null}, " +
                "hasAction=${action != null}, " +
                "timeout=$timeout)"
    }
}

typealias Action<T> = (T) -> Unit
typealias Guard<T> = (T) -> Boolean

data class Timeout(
    val value: Long,
    val unit: TimeUnit = TimeUnit.SECONDS
)
