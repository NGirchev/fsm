package io.github.ngirchev.fsm.impl.extended

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import io.github.ngirchev.fsm.it.document.Document
import io.github.ngirchev.fsm.it.document.DocumentState
import kotlin.test.assertEquals

@ExtendWith(MockKExtension::class)
class ExDomainFsmTest {

    @MockK
    lateinit var exTransitionTable: ExTransitionTable<DocumentState, String>

    @BeforeEach
    fun setUp() = MockKAnnotations.init(this)

    @Test
    fun handleWhenEventIsInvalidThenThrowException() {
        every { exTransitionTable.getTransitionByEvent(any(), any()) } returns ExTransition(
            from = DocumentState.NEW,
            to = DocumentState.READY_FOR_SIGN
        )
        val fsm = ExDomainFsm(exTransitionTable, autoTransitionEnabled = false)

        val document = Document()
        fsm.handle(document, "RUN")
        assertEquals(DocumentState.READY_FOR_SIGN, document.state)
    }

    @Test
    fun handleWhenAutoTransitionEnabledIsTrueThenUseTrue() {
        val transitionTable = ExTransitionTable.Builder<DocumentState, String>()
            .add(DocumentState.NEW, "RUN", DocumentState.READY_FOR_SIGN)
            .autoTransitionEnabled(false)
            .build()
        val fsm = ExDomainFsm(transitionTable, autoTransitionEnabled = true)

        val document = Document()
        fsm.handle(document, "RUN")
        assertEquals(DocumentState.READY_FOR_SIGN, document.state)
    }

    @Test
    fun handleWhenAutoTransitionEnabledIsNullThenUseTableValue() {
        val transitionTable = ExTransitionTable.Builder<DocumentState, String>()
            .add(DocumentState.NEW, "RUN", DocumentState.READY_FOR_SIGN)
            .autoTransitionEnabled(true)
            .build()
        val fsm = ExDomainFsm(transitionTable, autoTransitionEnabled = null)

        val document = Document()
        fsm.handle(document, "RUN")
        assertEquals(DocumentState.READY_FOR_SIGN, document.state)
    }

    @Test
    fun changeStateWhenAutoTransitionEnabledIsTrueThenUseTrue() {
        val transitionTable = ExTransitionTable.Builder<DocumentState, String>()
            .add(DocumentState.NEW, null, DocumentState.READY_FOR_SIGN)
            .autoTransitionEnabled(false)
            .build()
        val fsm = ExDomainFsm(transitionTable, autoTransitionEnabled = true)

        val document = Document()
        document.state = DocumentState.NEW
        fsm.changeState(document, DocumentState.READY_FOR_SIGN)
        assertEquals(DocumentState.READY_FOR_SIGN, document.state)
    }

    @Test
    fun changeStateWhenAutoTransitionEnabledIsNullThenUseTableValue() {
        val transitionTable = ExTransitionTable.Builder<DocumentState, String>()
            .add(DocumentState.NEW, null, DocumentState.READY_FOR_SIGN)
            .autoTransitionEnabled(true)
            .build()
        val fsm = ExDomainFsm(transitionTable, autoTransitionEnabled = null)

        val document = Document()
        document.state = DocumentState.NEW
        fsm.changeState(document, DocumentState.READY_FOR_SIGN)
        assertEquals(DocumentState.READY_FOR_SIGN, document.state)
    }

    @Test
    fun changeStateWhenAutoTransitionEnabledIsFalseThenUseFalse() {
        val transitionTable = ExTransitionTable.Builder<DocumentState, String>()
            .add(DocumentState.NEW, null, DocumentState.READY_FOR_SIGN)
            .autoTransitionEnabled(false)
            .build()
        val fsm = ExDomainFsm(transitionTable, autoTransitionEnabled = false)

        val document = Document()
        document.state = DocumentState.NEW
        fsm.changeState(document, DocumentState.READY_FOR_SIGN)
        assertEquals(DocumentState.READY_FOR_SIGN, document.state)
    }
}