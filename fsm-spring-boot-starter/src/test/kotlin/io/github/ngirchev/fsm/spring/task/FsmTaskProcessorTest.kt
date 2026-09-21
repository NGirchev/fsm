package io.github.ngirchev.fsm.spring.task

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.util.Optional

class FsmTaskProcessorTest {
    private val store = mock<FsmTaskStore<String>>()
    private val handler = mock<FsmTaskHandler<String>>()
    private val processor = FsmTaskProcessor(store, handler)

    @Test
    fun `returns false when no task is available`() {
        `when`(store.claimNextPending()).thenReturn(Optional.empty())

        assertThat(processor.processNext()).isFalse()

        verifyNoInteractions(handler)
        verify(store, never()).complete("task")
    }

    @Test
    fun `handles and completes a claimed task`() {
        `when`(store.claimNextPending()).thenReturn(Optional.of("task"))

        assertThat(processor.processNext()).isTrue()

        inOrder(handler, store).run {
            verify(handler).handle("task")
            verify(store).complete("task")
        }
    }

    @Test
    fun `does not complete a task when handling fails`() {
        `when`(store.claimNextPending()).thenReturn(Optional.of("task"))
        doThrow(IllegalStateException("failed")).`when`(handler).handle("task")

        assertThatThrownBy(processor::processNext)
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("failed")
        verify(store, never()).complete("task")
    }
}
