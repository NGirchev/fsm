package ru.girchev.fsm.test

import org.junit.jupiter.api.Test
import ru.girchev.fsm.FSM
import ru.girchev.fsm.Transition
import ru.girchev.fsm.TransitionTable
import ru.girchev.fsm.exception.FSMException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class FsmIT {

    @Test
    fun fullPath_Success() {
        val fsm = FSM(
            "NEW",
            TransitionTable.Builder<String, String>()
                .add(Transition(from = "NEW", to = "READY_FOR_SIGN", event = "TO_READY"))
                .add(Transition(from = "READY_FOR_SIGN", to = "SIGNED", event = "USER_SIGN"))
                .add(Transition(from = "READY_FOR_SIGN", to = "CANCELED", event = "FAILED_EVENT"))
                .add(Transition(from = "SIGNED", to = "AUTO_SENT"))
                .add(Transition(from = "AUTO_SENT", to = "DONE", event = "SUCCESS_EVENT"))
                .add(Transition(from = "AUTO_SENT", to = "CANCELED", event = "FAILED_EVENT"))
                .build(),
        )
        println("Initial state ${fsm.getState()}")
        try {
            fsm.handle("FAILED_EVENT")
        } catch (ex: Exception) {
            println("$ex")
            assertNotNull(ex)
            assertTrue(ex is FSMException)
        }

        assertEquals("NEW", fsm.getState())
        println("State still the same ${fsm.getState()}")

        fsm.handle("TO_READY")
        assertEquals("READY_FOR_SIGN", fsm.getState())

        fsm.handle("USER_SIGN")
        assertEquals("AUTO_SENT", fsm.getState())

        fsm.handle("SUCCESS_EVENT")
        assertEquals("DONE", fsm.getState())
        println("Terminal state ${fsm.getState()}")
    }
}
