package ru.girchev.fsm

import ru.girchev.fsm.impl.basic.BTransitionTable
import ru.girchev.fsm.impl.basic.from
import ru.girchev.fsm.impl.extended.ExTransitionTable
import ru.girchev.fsm.impl.extended.from

object FsmFactory {

    fun <STATE> states(): BTransitionTable.Builder<STATE> {
        return BTransitionTable.Builder()
    }

    fun <STATE, EVENT> statesWithEvents(): ExTransitionTable.Builder<STATE, EVENT> {
        return ExTransitionTable.Builder()
    }

    fun <STATE> from(s: STATE): ru.girchev.fsm.impl.basic.FromBuilder<STATE> {
        return states<STATE>().from(s)
    }

    fun <STATE, EVENT> from(s: STATE, e: EVENT): ru.girchev.fsm.impl.extended.FromBuilder<STATE, EVENT> {
        return statesWithEvents<STATE, EVENT>().from(s)
    }
}