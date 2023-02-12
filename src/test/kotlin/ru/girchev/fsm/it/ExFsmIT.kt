package ru.girchev.fsm.it

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import ru.girchev.fsm.impl.extended.ExFSM
import ru.girchev.fsm.impl.extended.ExTransition
import ru.girchev.fsm.impl.extended.ExTransitionTable
import ru.girchev.fsm.exception.FSMException
import kotlin.test.assertEquals

internal class ExFsmIT {

    private val transitions = ExTransitionTable.Builder<String, String>()
        .add(ExTransition(from = "NEW", to = "READY_FOR_SIGN", event = "TO_READY"))
        .add(ExTransition(from = "READY_FOR_SIGN", to = "SIGNED", event = "USER_SIGN"))
        .add(ExTransition(from = "READY_FOR_SIGN", to = "CANCELED", event = "FAILED_EVENT"))
        .add(ExTransition(from = "SIGNED", to = "AUTO_SENT"))
        .add(ExTransition(from = "AUTO_SENT", to = "DONE", event = "SUCCESS_EVENT"))
        .add(ExTransition(from = "AUTO_SENT", to = "CANCELED", event = "FAILED_EVENT"))
        .build()

    @Test
    fun shouldThrowFSMExceptionWhenFailedEvent() {
        // given
        val exFsm = ExFSM("NEW", transitions)
        // when
        Assertions.assertThrows(FSMException::class.java) {
            exFsm.onEvent("FAILED_EVENT")
        }
        // then
        assertEquals("NEW", exFsm.getState())
    }

    @Test
    fun shouldChangeStatusToReadyForSignWhenToReadyEvent() {
        // given
        val exFsm = ExFSM("NEW", transitions)
        // when
        exFsm.onEvent("TO_READY")
        // then
        assertEquals("READY_FOR_SIGN", exFsm.getState())
    }

    @Test
    fun shouldChangeStatusToAutoSentWhenUserSignEvent() {
        // given
        val exFsm = ExFSM("READY_FOR_SIGN", transitions)
        // when
        exFsm.onEvent("USER_SIGN")
        // then
        assertEquals("AUTO_SENT", exFsm.getState())
    }

    @Test
    fun shouldChangeStatusToDoneWhenSuccessEvent() {
        // given
        val exFsm = ExFSM("AUTO_SENT", transitions)
        // when
        exFsm.onEvent("SUCCESS_EVENT")
        // then
        assertEquals("DONE", exFsm.getState())
    }
}
