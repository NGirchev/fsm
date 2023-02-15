package ru.girchev.fsm.impl.basic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import ru.girchev.fsm.To
import ru.girchev.fsm.impl.extended.ExTransition
import ru.girchev.fsm.exception.FsmException
import ru.girchev.fsm.exception.FsmTransitionFailedException

class BFsmTest {

    @Test
    @DisplayName("Should throw an exception when the transition is invalid")
    fun toWhenTransitionIsInvalidThenThrowException() {
        val transitionTable = mock<BTransitionTable<String>>()
        val BFsm = BFsm("A", transitionTable)

        val exception = assertThrows(FsmTransitionFailedException::class.java) {
            BFsm.toState("B")
        }

        assertEquals("Illegal state transition A->B", exception.message)
    }

    @Test
    @DisplayName("Should throw an exception when the transition has not expected source")
    fun toWhenTransitionSourceIsNotTheSameAsExpectedThenThrowException() {
        val transitionTable = mock<BTransitionTable<String>> {
            on { getTransitionByState(any(), eq("B")) } doReturn (ExTransition<String, String>(from = "B", to = To("A")))
        }
        val BFsm = BFsm("A", transitionTable)

        val exception = assertThrows(FsmException::class.java) {
            BFsm.toState("B")
        }

        assertEquals("Current state A doesn't fit to change, because transition from=[B]", exception.message)
    }
}