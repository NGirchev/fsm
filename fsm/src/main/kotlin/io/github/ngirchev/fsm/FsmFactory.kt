package io.github.ngirchev.fsm

import io.github.ngirchev.fsm.impl.basic.BTransitionTable
import io.github.ngirchev.fsm.impl.extended.ExTransitionTable

object FsmFactory {

    fun <STATE> states(): BTransitionTable.Builder<STATE> {
        return BTransitionTable.Builder()
    }

    fun <STATE, EVENT> statesWithEvents(): ExTransitionTable.Builder<STATE, EVENT> {
        return ExTransitionTable.Builder()
    }
}