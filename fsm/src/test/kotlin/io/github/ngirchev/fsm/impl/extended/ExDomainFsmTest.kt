package io.github.ngirchev.fsm.impl.extended

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import io.github.ngirchev.fsm.StateChangeListener
import io.github.ngirchev.fsm.StateContext
import io.github.ngirchev.fsm.exception.FsmException
import io.github.ngirchev.fsm.it.document.Document
import io.github.ngirchev.fsm.it.document.DocumentState
import org.junit.jupiter.api.Assertions.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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
        every { exTransitionTable.getAutoTransition(any(), any()) } returns null
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

    @Test
    fun subclassShouldCreateCustomFsmRuntime() {
        val transitionTable = ExTransitionTable.Builder<DocumentState, String>()
            .add(DocumentState.NEW, "RUN", DocumentState.READY_FOR_SIGN)
            .autoTransitionEnabled(false)
            .build()
        val fsm = CustomExDomainFsm(transitionTable)

        val document = Document()
        fsm.handle(document, "RUN")

        assertEquals(DocumentState.READY_FOR_SIGN, document.state)
        assertNotNull(fsm.createdFsm)
        assertEquals(true, fsm.createdAutoTransitionEnabled)
    }

    @Test
    fun subclassShouldOverrideHandleFlow() {
        val transitionTable = ExTransitionTable.Builder<DocumentState, String>()
            .add(DocumentState.NEW, "RUN", DocumentState.READY_FOR_SIGN)
            .build()
        val fsm = HandleTrackingExDomainFsm(transitionTable)
        val document = Document()

        fsm.handle(document, "RUN")

        assertEquals(DocumentState.READY_FOR_SIGN, document.state)
        assertEquals(1, fsm.handleCalls)
    }

    private class CustomExDomainFsm(
        transitionTable: ExTransitionTable<DocumentState, String>,
    ) : ExDomainFsm<Document, DocumentState, String>(
        transitionTable,
        autoTransitionEnabled = true,
    ) {
        var createdFsm: CustomExFsm? = null
            private set
        var createdAutoTransitionEnabled: Boolean? = null
            private set

        override fun createFsm(
            domain: Document,
            autoTransitionEnabled: Boolean,
        ): ExFsm<DocumentState, String> {
            createdAutoTransitionEnabled = autoTransitionEnabled
            return CustomExFsm(domain, transitionTable, autoTransitionEnabled)
                .also { createdFsm = it }
        }
    }

    private class CustomExFsm(
        context: StateContext<DocumentState>,
        transitionTable: ExTransitionTable<DocumentState, String>,
        autoTransitionEnabled: Boolean,
    ) : ExFsm<DocumentState, String>(context, transitionTable, autoTransitionEnabled)

    private class HandleTrackingExDomainFsm(
        transitionTable: ExTransitionTable<DocumentState, String>,
    ) : ExDomainFsm<Document, DocumentState, String>(transitionTable) {
        var handleCalls: Int = 0
            private set

        override fun handle(
            domain: Document,
            event: String,
        ) {
            handleCalls++
            super.handle(domain, event)
        }
    }

    @Test
    fun handleShouldRemoveForwardingListenerAfterImmediateAutoTransitionsComplete() {
        val transitionTable = ExTransitionTable.Builder<DocumentState, String>()
            .autoTransitionEnabled(true)
            .add(DocumentState.NEW, "RUN", DocumentState.READY_FOR_SIGN)
            .add(DocumentState.READY_FOR_SIGN, null, DocumentState.SIGNED)
            .build()
        val fsm = TrackingExDomainFsm(transitionTable, autoTransitionEnabled = true)

        fsm.handle(Document(), "RUN")

        assertEquals(1, fsm.createdFsm?.removeStateChangeListenerCount)
        assertEquals(1, fsm.createdFsm?.removeAutoTransitionCompletionListenerCount)
    }

    private class TrackingExDomainFsm(
        transitionTable: ExTransitionTable<DocumentState, String>,
        autoTransitionEnabled: Boolean,
    ) : ExDomainFsm<Document, DocumentState, String>(
        transitionTable,
        autoTransitionEnabled,
    ) {
        var createdFsm: TrackingExFsm? = null
            private set

        override fun createFsm(
            domain: Document,
            autoTransitionEnabled: Boolean,
        ): ExFsm<DocumentState, String> {
            return TrackingExFsm(domain, transitionTable, autoTransitionEnabled)
                .also { createdFsm = it }
        }
    }

    private class TrackingExFsm(
        context: StateContext<DocumentState>,
        transitionTable: ExTransitionTable<DocumentState, String>,
        autoTransitionEnabled: Boolean,
    ) : ExFsm<DocumentState, String>(context, transitionTable, autoTransitionEnabled) {
        var removeStateChangeListenerCount = 0
            private set
        var removeAutoTransitionCompletionListenerCount = 0
            private set

        override fun removeStateChangeListener(listener: StateChangeListener<DocumentState>) {
            removeStateChangeListenerCount++
            super.removeStateChangeListener(listener)
        }

        override fun removeAutoTransitionCompletionListener(listener: () -> Unit) {
            removeAutoTransitionCompletionListenerCount++
            super.removeAutoTransitionCompletionListener(listener)
        }
    }
}
