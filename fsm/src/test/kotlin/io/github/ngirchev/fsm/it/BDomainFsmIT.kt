package io.github.ngirchev.fsm.it

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.github.ngirchev.fsm.StateContext
import io.github.ngirchev.fsm.exception.FsmException
import io.github.ngirchev.fsm.impl.basic.BDomainFsm
import io.github.ngirchev.fsm.impl.basic.BTransitionTable
import io.github.ngirchev.fsm.it.document.Document
import io.github.ngirchev.fsm.it.document.DocumentState
import io.github.ngirchev.fsm.it.document.DocumentState.*
import kotlin.test.assertEquals

internal class BDomainFsmIT {

    private lateinit var document: Document
    private val fsm: BDomainFsm<StateContext<DocumentState>, DocumentState> = BDomainFsm(
        BTransitionTable.Builder<DocumentState>()
            .autoTransitionEnabled(false)
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
    fun shouldThrowFsmExceptionWhenTryToSign() {
        // given
        // when
        Assertions.assertThrows(FsmException::class.java) {
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
    fun shouldChangeStatusToReadyForSignWithAutoTransition() {
        val fsm: BDomainFsm<StateContext<DocumentState>, DocumentState> = BDomainFsm(
            BTransitionTable.Builder<DocumentState>()
                .autoTransitionEnabled(true)
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
        // given
        // when
        fsm.changeState(document, READY_FOR_SIGN)
        // then
        assertEquals(DONE, document.state)
    }

    @Test
    fun shouldChangeStatusToReadyForSignWithAutoTransitionOnFsm() {
        val fsm: BDomainFsm<StateContext<DocumentState>, DocumentState> = BDomainFsm(
            BTransitionTable.Builder<DocumentState>()
                .autoTransitionEnabled(false)
                .add(from = NEW, READY_FOR_SIGN)
                .add(
                    from = READY_FOR_SIGN,
                    SIGNED, CANCELED
                )
                .add(
                    from = SIGNED,
                    DONE, CANCELED
                )
                .build(),
            autoTransitionEnabled = true
        )
        // given
        // when
        fsm.changeState(document, READY_FOR_SIGN)
        // then
        assertEquals(DONE, document.state)
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
