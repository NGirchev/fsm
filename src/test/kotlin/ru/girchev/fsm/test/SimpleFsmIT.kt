package ru.girchev.fsm.test

import org.junit.jupiter.api.Test
import ru.girchev.fsm.core.SimpleFSM
import ru.girchev.fsm.core.SimpleTransition
import ru.girchev.fsm.core.SimpleTransitionTable.*
import ru.girchev.fsm.core.from
import ru.girchev.fsm.exception.DuplicateTransitionException
import ru.girchev.fsm.exception.FSMException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class SimpleFsmIT {

    @Test
    fun fullPath_Success() {
        val autoSendEnabled = true
        var successfullySent = false
        val fsm = SimpleFSM("NEW",
            Builder<String>()
                .add(from = "NEW", "READY_FOR_SIGN")
                .add(from = "READY_FOR_SIGN", "SIGNED", "CANCELED")
                .add(SimpleTransition(from = "SIGNED",
                    to = "AUTO_SENT",
                    condition = { autoSendEnabled },
                    action = { successfullySent = true; println("AUTO SENT ACTION") }
                ))
                .add(from = "SIGNED", "DONE", "CANCELED")
                .add(from = "AUTO_SENT", "DONE", "CANCELED")
                .build())
        println("Initial state ${fsm.getState()}")

        try {
            fsm.toState("SIGNED")
        } catch (ex: Exception) {
            println("$ex")
            assertNotNull(ex)
            assertTrue(ex is FSMException)
        }

        assertEquals("NEW", fsm.getState())
        println("State still the same ${fsm.getState()}")

        fsm.toState("READY_FOR_SIGN")
        assertEquals("READY_FOR_SIGN", fsm.getState())

        fsm.toState("SIGNED")
        assertEquals("SIGNED", fsm.getState())

        fsm.toState("AUTO_SENT")
        assertEquals("AUTO_SENT", fsm.getState())
        assertEquals(true, successfullySent)

        fsm.toState("CANCELED")
        assertEquals("CANCELED", fsm.getState())
        println("Terminal state ${fsm.getState()}")
    }

    @Test
    fun tryToCreateWithDuplicateStates_Failed() {
        assertFailsWith(
            exceptionClass = DuplicateTransitionException::class,
            message = "No exception found",
            block = {
                SimpleFSM(
                    "NEW",
                    Builder<String>()
                        .add(from = "NEW", To("READY_FOR_SIGN"))
                        .add(from = "NEW", To("READY_FOR_SIGN"))
                        .build()
                )
            }
        )
    }

    @Test
    fun alternativeBuilder_Success() {
        val autoSendEnabled = true
        var successfullySent = false
        val fsm = SimpleFSM(
            "NEW",
            Builder<String>()
                .from("NEW").to("SIGNED").end()
                .from("SIGNED").to("AUTO_SENT")
                .withCondition { autoSendEnabled }
                .withAction { successfullySent = true; println("AUTO SENT ACTION") }.end()
                .from("AUTO_SENT").toSwitch()
                .case("DONE").endCase()
                .case("CANCELED").withAction { println("CANCEL") }.endCase()
                .endSwitch()
                .build()
        )
        fsm.toState("SIGNED")
        assertEquals("SIGNED", fsm.getState())

        fsm.toState("AUTO_SENT")
        assertEquals("AUTO_SENT", fsm.getState())
        assertEquals(true, successfullySent)

        fsm.toState("CANCELED")
        assertEquals("CANCELED", fsm.getState())
    }
}
