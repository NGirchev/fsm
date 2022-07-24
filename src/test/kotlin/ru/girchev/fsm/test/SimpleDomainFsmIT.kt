package ru.girchev.fsm.test

import org.junit.jupiter.api.Test
import ru.girchev.fsm.test.document.DocumentState.*
import ru.girchev.fsm.core.SimpleTransitionTable
import ru.girchev.fsm.core.SimpleDomainFSM
import ru.girchev.fsm.exception.FSMException
import ru.girchev.fsm.test.document.Document
import ru.girchev.fsm.test.document.DocumentState
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class SimpleDomainFsmIT {

    @Test
    fun fullPath_Success() {
        val document = Document()
        val fsm = SimpleDomainFSM(
            SimpleTransitionTable.Builder<DocumentState>()
                .add(from = NEW, READY_FOR_SIGN)
                .add(from = READY_FOR_SIGN,
                    SIGNED, CANCELED
                )
                .add(from = SIGNED,
                    DONE, CANCELED
                )
                .build())
        println("Initial state ${document.state}")
        try {
            fsm.changeState(document, SIGNED)
        } catch (ex: Exception) {
            println("$ex")
            assertNotNull(ex)
            assertTrue(ex is FSMException)
        }

        assertEquals(NEW, document.state)
        println("State still the same ${document.state}")

        fsm.changeState(document, READY_FOR_SIGN)
        assertEquals(READY_FOR_SIGN, document.state)

        fsm.changeState(document, CANCELED)
        assertEquals(CANCELED, document.state)
        println("Terminal state ${document.state}")
    }
}
