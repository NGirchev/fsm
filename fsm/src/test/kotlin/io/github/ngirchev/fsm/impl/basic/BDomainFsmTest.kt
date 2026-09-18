package io.github.ngirchev.fsm.impl.basic

import org.junit.jupiter.api.Test
import io.github.ngirchev.fsm.AutoTransitionScheduler
import io.github.ngirchev.fsm.StateContext
import io.github.ngirchev.fsm.it.document.Document
import io.github.ngirchev.fsm.it.document.DocumentState
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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

    @Test
    fun subclassShouldCreateCustomFsmRuntime() {
        val transitionTable = BTransitionTable.Builder<DocumentState>()
            .add(DocumentState.NEW, DocumentState.READY_FOR_SIGN)
            .autoTransitionEnabled(false)
            .build()
        val scheduler = AutoTransitionScheduler<DocumentState> { _, _, runTransition -> runTransition() }
        val fsm = CustomBDomainFsm(transitionTable, scheduler)

        val document = Document()
        fsm.changeState(document, DocumentState.READY_FOR_SIGN)

        assertEquals(DocumentState.READY_FOR_SIGN, document.state)
        assertNotNull(fsm.createdFsm)
        assertEquals(true, fsm.createdAutoTransitionEnabled)
    }

    private class CustomBDomainFsm(
        transitionTable: BTransitionTable<DocumentState>,
        scheduler: AutoTransitionScheduler<DocumentState>,
    ) : BDomainFsm<Document, DocumentState>(
        transitionTable,
        autoTransitionEnabled = true,
        autoTransitionScheduler = scheduler,
    ) {
        var createdFsm: CustomBFsm? = null
            private set
        var createdAutoTransitionEnabled: Boolean? = null
            private set

        override fun createFsm(
            domain: Document,
            autoTransitionEnabled: Boolean,
        ): BFsm<DocumentState> {
            createdAutoTransitionEnabled = autoTransitionEnabled
            return CustomBFsm(domain, transitionTable, autoTransitionEnabled, autoTransitionScheduler)
                .also { createdFsm = it }
        }
    }

    private class CustomBFsm(
        context: StateContext<DocumentState>,
        transitionTable: BTransitionTable<DocumentState>,
        autoTransitionEnabled: Boolean,
        autoTransitionScheduler: AutoTransitionScheduler<DocumentState>,
    ) : BFsm<DocumentState>(context, transitionTable, autoTransitionEnabled, autoTransitionScheduler)
}
