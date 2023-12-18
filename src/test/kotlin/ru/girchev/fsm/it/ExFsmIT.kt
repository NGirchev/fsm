package ru.girchev.fsm.it

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import ru.girchev.fsm.FsmFactory
import ru.girchev.fsm.exception.FsmException
import ru.girchev.fsm.impl.extended.ExFsm
import ru.girchev.fsm.impl.extended.ExTransition
import kotlin.test.assertEquals

internal class ExFsmIT {

    private val transitions = FsmFactory.statesWithEvents(String::class.java, String::class.java)
        .add(ExTransition(from = "NEW", to = "READY_FOR_SIGN", event = "TO_READY"))
        .add(ExTransition(from = "READY_FOR_SIGN", to = "SIGNED", event = "USER_SIGN"))
        .add(ExTransition(from = "READY_FOR_SIGN", to = "CANCELED", event = "FAILED_EVENT"))
        .add(ExTransition(from = "SIGNED", to = "AUTO_SENT"))
        .add(ExTransition(from = "AUTO_SENT", to = "DONE", event = "SUCCESS_EVENT"))
        .add(ExTransition(from = "AUTO_SENT", to = "CANCELED", event = "FAILED_EVENT"))
        .build()

    @Test
    fun shouldThrowFsmExceptionWhenFailedEvent() {
        // given
        val exFsm = ExFsm("NEW", transitions)
        // when
        Assertions.assertThrows(FsmException::class.java) {
            exFsm.onEvent("FAILED_EVENT")
        }
        // then
        assertEquals("NEW", exFsm.getState())
    }

    @Test
    fun shouldChangeStatusToReadyForSignWhenToReadyEvent() {
        // given
        val exFsm = ExFsm("NEW", transitions)
        // when
        exFsm.onEvent("TO_READY")
        // then
        assertEquals("READY_FOR_SIGN", exFsm.getState())
    }

    @Test
    fun shouldChangeStatusToAutoSentWhenUserSignEvent() {
        // given
        val exFsm = ExFsm("READY_FOR_SIGN", transitions)
        // when
        exFsm.onEvent("USER_SIGN")
        // then
        assertEquals("AUTO_SENT", exFsm.getState())
    }

    @Test
    fun shouldChangeStatusToDoneWhenSuccessEvent() {
        // given
        val exFsm = ExFsm("AUTO_SENT", transitions)
        // when
        exFsm.onEvent("SUCCESS_EVENT")
        // then
        assertEquals("DONE", exFsm.getState())
    }
}
