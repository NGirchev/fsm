package ru.girchev.fsm.it

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.girchev.fsm.FSMContext
import ru.girchev.fsm.core.BDomainFSM
import ru.girchev.fsm.core.BTransitionTable
import ru.girchev.fsm.exception.FSMException
import ru.girchev.fsm.it.document.Document
import ru.girchev.fsm.it.document.DocumentState
import ru.girchev.fsm.it.document.DocumentState.*
import kotlin.test.assertEquals

internal class BaseDomainFsmIT {

    private lateinit var document: Document
    private val fsm: BDomainFSM<FSMContext<DocumentState>, DocumentState> = BDomainFSM(
        BTransitionTable.Builder<DocumentState>()
            .add(from = NEW, READY_FOR_SIGN)
            .add(
                from = READY_FOR_SIGN,
                SIGNED, CANCELED
            )
            .add(
                from = SIGNED,
                DONE, CANCELED
            )
            .build()
    )

    @BeforeEach
    fun init() {
        document = Document()
    }

    @Test
    fun shouldThrowFSMExceptionWhenTryToSign() {
        // given
        // when
        Assertions.assertThrows(FSMException::class.java) {
            fsm.changeState(document, SIGNED)
        }
        assertEquals(NEW, document.state)
    }

    @Test
    fun shouldChangeStatusToReadyForSign() {
        // given
        // when
        fsm.changeState(document, READY_FOR_SIGN)
        // then
        assertEquals(READY_FOR_SIGN, document.state)
    }

    @Test
    fun shouldChangeStatusToSignedAfterReadyForSign() {
        // given
        document.state = READY_FOR_SIGN
        // when
        fsm.changeState(document, SIGNED)
        // then
        assertEquals(SIGNED, document.state)
    }

    @Test
    fun shouldChangeStatusToCancelledAfterReadyForSign() {
        // given
        document.state = READY_FOR_SIGN
        // when
        fsm.changeState(document, CANCELED)
        // then
        assertEquals(CANCELED, document.state)
    }

    @Test
    fun shouldChangeStatusToDoneAfterSigned() {
        // given
        document.state = SIGNED
        // when
        fsm.changeState(document, DONE)
        // then
        assertEquals(DONE, document.state)
    }

    @Test
    fun shouldChangeStatusToCancelledAfterSigned() {
        // given
        document.state = SIGNED
        // when
        fsm.changeState(document, CANCELED)
        // then
        assertEquals(CANCELED, document.state)
    }
}
