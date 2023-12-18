package ru.girchev.fsm

import ru.girchev.fsm.impl.basic.BTransitionTable
import ru.girchev.fsm.impl.extended.ExTransitionTable

object FsmFactory {

    fun <STATE> states(stateType: Class<STATE>? = null): BTransitionTable.Builder<STATE> {
        return BTransitionTable.Builder()
    }

    fun <STATE, EVENT> statesWithEvents(
        stateType: Class<STATE>? = null,
        eventType: Class<EVENT>? = null
    ): ExTransitionTable.Builder<STATE, EVENT> {
        return ExTransitionTable.Builder()
    }
}