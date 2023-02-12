package ru.girchev.fsm

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import ru.girchev.fsm.it.document.Document
import ru.girchev.fsm.it.document.DocumentState
import kotlin.test.assertEquals

@ExtendWith(MockKExtension::class)
class DomainFSMTest {

    @MockK
    lateinit var transitionTable: TransitionTable<DocumentState, String>

    @BeforeEach
    fun setUp() = MockKAnnotations.init(this)

    @Test
    fun handleWhenEventIsInvalidThenThrowException() {
        every { transitionTable.getTransition(any(), any()) } returns Transition(
            from = DocumentState.NEW,
            to = DocumentState.READY_FOR_SIGN
        )
        val fsm = DomainFSM(transitionTable, autoTransitionEnabled = false)

        val document = Document()
        fsm.handle(document, "RUN")
        assertEquals(DocumentState.READY_FOR_SIGN, document.state)
    }

}