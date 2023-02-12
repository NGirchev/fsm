package ru.girchev.fsm.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import ru.girchev.fsm.Transition
import ru.girchev.fsm.TransitionTable
import ru.girchev.fsm.exception.FSMException
import ru.girchev.fsm.exception.FSMTransitionFailedException

class BaseFSMTest {

    @Test
    @DisplayName("Should throw an exception when the transition is invalid")
    fun toWhenTransitionIsInvalidThenThrowException() {
        val transitionTable = mock<TransitionTable<String, String>>()
        val baseFSM = BaseFSM("A", transitionTable)

        val exception = assertThrows(FSMTransitionFailedException::class.java) {
            baseFSM.to("B")
        }

        assertEquals("Illegal state transition A->B", exception.message)
    }

    @Test
    @DisplayName("Should throw an exception when the transition has not expected source")
    fun toWhenTransitionSourceIsNotTheSameAsExpectedThenThrowException() {
        val transitionTable = mock<TransitionTable<String, String>> {
            on { getTransitionByState(any(), eq("B")) } doReturn (Transition<String, String>(from = "B", to = "A"))
        }
        val baseFSM = BaseFSM("A", transitionTable)

        val exception = assertThrows(FSMException::class.java) {
            baseFSM.to("B")
        }

        assertEquals("Current state A doesn't fit to change, because transition from=[B]", exception.message)
    }
}