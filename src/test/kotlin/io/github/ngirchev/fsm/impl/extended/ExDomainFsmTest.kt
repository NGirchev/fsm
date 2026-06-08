package io.github.ngirchev.fsm.impl.extended

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import io.github.ngirchev.fsm.AutoTransitionScheduler
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
        val scheduler = AutoTransitionScheduler<DocumentState> { _, _, runTransition -> runTransition() }
        val fsm = CustomExDomainFsm(transitionTable, scheduler)

        val document = Document()
        fsm.handle(document, "RUN")

        assertEquals(DocumentState.READY_FOR_SIGN, document.state)
        assertNotNull(fsm.createdFsm)
        assertEquals(true, fsm.createdAutoTransitionEnabled)
    }

    private class CustomExDomainFsm(
        transitionTable: ExTransitionTable<DocumentState, String>,
        scheduler: AutoTransitionScheduler<DocumentState>,
    ) : ExDomainFsm<Document, DocumentState, String>(
        transitionTable,
        autoTransitionEnabled = true,
        autoTransitionScheduler = scheduler,
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
            return CustomExFsm(domain, transitionTable, autoTransitionEnabled, autoTransitionScheduler)
                .also { createdFsm = it }
        }
    }

    private class CustomExFsm(
        context: StateContext<DocumentState>,
        transitionTable: ExTransitionTable<DocumentState, String>,
        autoTransitionEnabled: Boolean,
        autoTransitionScheduler: AutoTransitionScheduler<DocumentState>,
    ) : ExFsm<DocumentState, String>(context, transitionTable, autoTransitionEnabled, autoTransitionScheduler)

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

    @Test
    fun handleShouldKeepForwardingListenerForScheduledAutoTransitionRetryUntilSuccess() {
        var autoTransitionAttempts = 0
        val callbacks = mutableListOf<() -> Unit>()
        val scheduler = AutoTransitionScheduler<DocumentState> { _, _, runTransition ->
            callbacks.add(runTransition)
        }
        val transitionTable = ExTransitionTable.Builder<DocumentState, String>()
            .autoTransitionEnabled(true)
            .add(DocumentState.NEW, "RUN", DocumentState.READY_FOR_SIGN)
            .add(
                from = DocumentState.READY_FOR_SIGN,
                onEvent = null,
                to = DocumentState.SIGNED,
                action = {
                    autoTransitionAttempts++
                    if (autoTransitionAttempts == 1) {
                        throw FsmException("retry")
                    }
                }
            )
            .build()
        val fsm = TrackingExDomainFsm(transitionTable, autoTransitionEnabled = true, scheduler)

        fsm.handle(Document(), "RUN")

        assertEquals(0, fsm.createdFsm?.removeStateChangeListenerCount)

        assertThrows(FsmException::class.java) {
            callbacks.single().invoke()
        }

        assertEquals(0, fsm.createdFsm?.removeStateChangeListenerCount)

        callbacks.single().invoke()

        assertEquals(1, fsm.createdFsm?.removeStateChangeListenerCount)
        assertEquals(1, fsm.createdFsm?.removeAutoTransitionCompletionListenerCount)
    }

    private class TrackingExDomainFsm(
        transitionTable: ExTransitionTable<DocumentState, String>,
        autoTransitionEnabled: Boolean,
        scheduler: AutoTransitionScheduler<DocumentState> = AutoTransitionScheduler { _, _, runTransition ->
            runTransition()
        },
    ) : ExDomainFsm<Document, DocumentState, String>(
        transitionTable,
        autoTransitionEnabled,
        scheduler,
    ) {
        var createdFsm: TrackingExFsm? = null
            private set

        override fun createFsm(
            domain: Document,
            autoTransitionEnabled: Boolean,
        ): ExFsm<DocumentState, String> {
            return TrackingExFsm(domain, transitionTable, autoTransitionEnabled, autoTransitionScheduler)
                .also { createdFsm = it }
        }
    }

    private class TrackingExFsm(
        context: StateContext<DocumentState>,
        transitionTable: ExTransitionTable<DocumentState, String>,
        autoTransitionEnabled: Boolean,
        autoTransitionScheduler: AutoTransitionScheduler<DocumentState>,
    ) : ExFsm<DocumentState, String>(context, transitionTable, autoTransitionEnabled, autoTransitionScheduler) {
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
