package io.github.ngirchev.fsm.impl.basic

import org.junit.jupiter.api.Test
import io.github.ngirchev.fsm.it.document.Document
import io.github.ngirchev.fsm.it.document.DocumentState
import kotlin.test.assertEquals

class BDomainFsmTest {

    @Test
    fun changeStateWhenAutoTransitionEnabledIsTrueThenUseTrue() {
        val transitionTable = BTransitionTable.Builder<DocumentState>()
            .add(DocumentState.NEW, DocumentState.READY_FOR_SIGN)
            .autoTransitionEnabled(false)
            .build()
        val fsm = BDomainFsm(transitionTable, autoTransitionEnabled = true)

        val document = Document()
        document.state = DocumentState.NEW
        fsm.changeState(document, DocumentState.READY_FOR_SIGN)
        assertEquals(DocumentState.READY_FOR_SIGN, document.state)
    }

    @Test
    fun changeStateWhenAutoTransitionEnabledIsNullThenUseTableValue() {
        val transitionTable = BTransitionTable.Builder<DocumentState>()
            .add(DocumentState.NEW, DocumentState.READY_FOR_SIGN)
            .autoTransitionEnabled(true)
            .build()
        val fsm = BDomainFsm(transitionTable, autoTransitionEnabled = null)

        val document = Document()
        document.state = DocumentState.NEW
        fsm.changeState(document, DocumentState.READY_FOR_SIGN)
        assertEquals(DocumentState.READY_FOR_SIGN, document.state)
    }

    @Test
    fun changeStateWhenAutoTransitionEnabledIsFalseThenUseFalse() {
        val transitionTable = BTransitionTable.Builder<DocumentState>()
            .add(DocumentState.NEW, DocumentState.READY_FOR_SIGN)
            .autoTransitionEnabled(false)
            .build()
        val fsm = BDomainFsm(transitionTable, autoTransitionEnabled = false)

        val document = Document()
        document.state = DocumentState.NEW
        fsm.changeState(document, DocumentState.READY_FOR_SIGN)
        assertEquals(DocumentState.READY_FOR_SIGN, document.state)
    }
}
