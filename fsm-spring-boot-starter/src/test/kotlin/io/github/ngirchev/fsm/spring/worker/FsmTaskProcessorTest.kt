package io.github.ngirchev.fsm.spring.worker

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.aop.framework.ProxyFactory
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
import org.springframework.transaction.interceptor.TransactionInterceptor
import org.springframework.transaction.support.SimpleTransactionStatus
import java.io.IOException
import java.util.Optional

class FsmTaskProcessorTest {
    private val store = mock<FsmTaskStore<String>>()
    private val handler = mock<FsmTaskHandler<String>>()
    private val transactions = mock<PlatformTransactionManager>()
    private val transaction = SimpleTransactionStatus()
    private val processor = transactionalProcessor(handler)

    @BeforeEach
    fun configureTransaction() {
        `when`(transactions.getTransaction(any(TransactionDefinition::class.java))).thenReturn(transaction)
    }

    @Test
    fun `returns false when no task is available`() {
        `when`(store.claimNextPending()).thenReturn(Optional.empty())

        assertThat(processor.processNext()).isFalse()

        verifyNoInteractions(handler)
        verify(store, never()).complete("task")
        verify(transactions).commit(transaction)
        verify(transactions, never()).rollback(transaction)
    }

    @Test
    fun `handles and completes a claimed task`() {
        `when`(store.claimNextPending()).thenReturn(Optional.of("task"))

        assertThat(processor.processNext()).isTrue()

        inOrder(handler, store).run {
            verify(handler).handle("task")
            verify(store).complete("task")
        }
        verify(transactions).commit(transaction)
        verify(transactions, never()).rollback(transaction)
    }

    @Test
    fun `does not complete a task when handling fails`() {
        `when`(store.claimNextPending()).thenReturn(Optional.of("task"))
        doThrow(IllegalStateException("failed")).`when`(handler).handle("task")

        assertThatThrownBy(processor::processNext)
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("failed")
        verify(store, never()).complete("task")
        verify(transactions).rollback(transaction)
        verify(transactions, never()).commit(transaction)
    }

    @Test
    fun `checked handler failure rolls back the transaction without completing the task`() {
        `when`(store.claimNextPending()).thenReturn(Optional.of("task"))
        val failure = IOException("Task input could not be read")
        val failingProcessor = transactionalProcessor { throw failure }

        assertThatThrownBy(failingProcessor::processNext).isSameAs(failure)

        verify(store, never()).complete("task")
        verify(transactions).rollback(transaction)
        verify(transactions, never()).commit(transaction)
    }

    private fun transactionalProcessor(taskHandler: FsmTaskHandler<String>): FsmTaskProcessor<*> {
        val advice = TransactionInterceptor().apply {
            transactionManager = transactions
            transactionAttributeSource = AnnotationTransactionAttributeSource()
        }
        return ProxyFactory(FsmTaskProcessor(store, taskHandler)).apply {
            isProxyTargetClass = true
            addAdvice(advice)
        }.proxy as FsmTaskProcessor<*>
    }
}
