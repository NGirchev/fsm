package ru.girchev.fsm.it

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import ru.girchev.fsm.FSM
import ru.girchev.fsm.Transition
import ru.girchev.fsm.TransitionTable
import ru.girchev.fsm.exception.FSMException
import kotlin.test.assertEquals

internal class FsmIT {

    private val transitions = TransitionTable.Builder<String, String>()
        .add(Transition(from = "NEW", to = "READY_FOR_SIGN", event = "TO_READY"))
        .add(Transition(from = "READY_FOR_SIGN", to = "SIGNED", event = "USER_SIGN"))
        .add(Transition(from = "READY_FOR_SIGN", to = "CANCELED", event = "FAILED_EVENT"))
        .add(Transition(from = "SIGNED", to = "AUTO_SENT"))
        .add(Transition(from = "AUTO_SENT", to = "DONE", event = "SUCCESS_EVENT"))
        .add(Transition(from = "AUTO_SENT", to = "CANCELED", event = "FAILED_EVENT"))
        .build()

    @Test
    fun shouldThrowFSMExceptionWhenFailedEvent() {
        // given
        val fsm = FSM("NEW", transitions)
        // when
        Assertions.assertThrows(FSMException::class.java) {
            fsm.on("FAILED_EVENT")
        }
        // then
        assertEquals("NEW", fsm.getState())
    }

    @Test
    fun shouldChangeStatusToReadyForSignWhenToReadyEvent() {
        // given
        val fsm = FSM("NEW", transitions)
        // when
        fsm.on("TO_READY")
        // then
        assertEquals("READY_FOR_SIGN", fsm.getState())
    }

    @Test
    fun shouldChangeStatusToAutoSentWhenUserSignEvent() {
        // given
        val fsm = FSM("READY_FOR_SIGN", transitions)
        // when
        fsm.on("USER_SIGN")
        // then
        assertEquals("AUTO_SENT", fsm.getState())
    }

    @Test
    fun shouldChangeStatusToDoneWhenSuccessEvent() {
        // given
        val fsm = FSM("AUTO_SENT", transitions)
        // when
        fsm.on("SUCCESS_EVENT")
        // then
        assertEquals("DONE", fsm.getState())
    }
}
