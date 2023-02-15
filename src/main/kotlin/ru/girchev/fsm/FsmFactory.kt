package ru.girchev.fsm

import ru.girchev.fsm.impl.basic.BTransitionTable
import ru.girchev.fsm.impl.extended.ExTransitionTable

object FsmFactory {

    fun <STATE> states(): BTransitionTable.Builder<STATE> {
        return BTransitionTable.Builder()
    }

    fun <STATE, EVENT> statesWithEvents(): ExTransitionTable.Builder<STATE, EVENT> {
        return ExTransitionTable.Builder()
    }
}