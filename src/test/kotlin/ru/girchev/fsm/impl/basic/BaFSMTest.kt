package ru.girchev.fsm.impl.basic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import ru.girchev.fsm.impl.extended.ExTransition
import ru.girchev.fsm.exception.FSMException
import ru.girchev.fsm.exception.FSMTransitionFailedException

class BaFSMTest {

    @Test
    @DisplayName("Should throw an exception when the transition is invalid")
    fun toWhenTransitionIsInvalidThenThrowException() {
        val transitionTable = mock<BaTransitionTable<String>>()
        val baFSM = BaFSM("A", transitionTable)

        val exception = assertThrows(FSMTransitionFailedException::class.java) {
            baFSM.toState("B")
        }

        assertEquals("Illegal state transition A->B", exception.message)
    }

    @Test
    @DisplayName("Should throw an exception when the transition has not expected source")
    fun toWhenTransitionSourceIsNotTheSameAsExpectedThenThrowException() {
        val transitionTable = mock<BaTransitionTable<String>> {
            on { getTransitionByState(any(), eq("B")) } doReturn (ExTransition<String, String>(from = "B", to = "A"))
        }
        val baFSM = BaFSM("A", transitionTable)

        val exception = assertThrows(FSMException::class.java) {
            baFSM.toState("B")
        }

        assertEquals("Current state A doesn't fit to change, because transition from=[B]", exception.message)
    }
}