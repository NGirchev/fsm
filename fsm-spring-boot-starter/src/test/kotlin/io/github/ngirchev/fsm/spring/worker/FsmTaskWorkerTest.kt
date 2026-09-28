package io.github.ngirchev.fsm.spring.worker

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.times
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import org.springframework.scheduling.TaskScheduler
import java.time.Duration
import java.io.IOException
import java.util.Optional
import java.util.concurrent.ScheduledFuture

class FsmTaskWorkerTest {
    private val scheduler = mock<TaskScheduler>()
    private val properties = FsmTaskWorkerProperties(Duration.ofMillis(250), 2)

    @Test
    fun `rejects non-positive worker settings`() {
        val zeroInterval = FsmTaskWorkerProperties(Duration.ZERO, 2)
        assertThatThrownBy { FsmTaskWorker(emptyList(), scheduler, zeroInterval) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("fsm.tasks.poll-interval must be positive")

        val zeroTaskLimit = FsmTaskWorkerProperties(Duration.ofSeconds(1), 0)
        assertThatThrownBy { FsmTaskWorker(emptyList(), scheduler, zeroTaskLimit) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("fsm.tasks.max-tasks-per-processor-per-poll must be positive")
    }

    @Test
    fun `poll drains each processor up to configured limit`() {
        val first = processor()
        val second = processor()
        `when`(first.processNext()).thenReturn(true, true)
        `when`(second.processNext()).thenReturn(true, false)
        val worker = FsmTaskWorker(listOf(first, second), scheduler, properties)

        worker.runOnce()

        verify(first, times(2)).processNext()
        verify(second, times(2)).processNext()
    }

    @Test
    fun `failed processor does not stop other processors`() {
        val failed = processor()
        val healthy = processor()
        `when`(failed.processNext()).thenThrow(IllegalStateException("failed"))
        `when`(healthy.processNext()).thenReturn(false)
        val worker = FsmTaskWorker(listOf(failed, healthy), scheduler, properties)

        worker.runOnce()

        verify(failed).processNext()
        verify(healthy).processNext()
        verifyNoMoreInteractions(failed, healthy)
    }

    @Test
    fun `checked failure allows other processors and retries on the next poll`() {
        val store = mock<FsmTaskStore<String>>()
        `when`(store.claimNextPending()).thenReturn(Optional.of("task"), Optional.of("task"), Optional.empty())
        var attempts = 0
        val retried = FsmTaskProcessor(store) {
            attempts++
            if (attempts == 1) throw IOException("Task input could not be read")
        }
        val healthy = processor()
        val worker = FsmTaskWorker(listOf(retried, healthy), scheduler, properties)

        worker.runOnce()

        assertThat(attempts).isEqualTo(1)
        verify(store, never()).complete("task")
        verify(healthy).processNext()

        worker.runOnce()

        assertThat(attempts).isEqualTo(2)
        verify(store).complete("task")
        verify(healthy, times(2)).processNext()
    }

    @Test
    fun `lifecycle schedules only once and cancels on stop`() {
        @Suppress("UNCHECKED_CAST")
        val future = mock<ScheduledFuture<Any>>() as ScheduledFuture<*>
        `when`(scheduler.scheduleWithFixedDelay(any(Runnable::class.java), any(Duration::class.java)))
            .thenReturn(future)
        val processor = processor()
        val worker = FsmTaskWorker(listOf(processor), scheduler, properties)

        assertThat(worker.isRunning).isFalse()
        worker.start()
        worker.start()
        assertThat(worker.isRunning).isTrue()
        worker.stop()
        worker.stop()
        assertThat(worker.isRunning).isFalse()

        val callback = ArgumentCaptor.forClass(Runnable::class.java)
        verify(scheduler).scheduleWithFixedDelay(callback.capture(), eq(Duration.ofMillis(250)))
        callback.value.run()
        verify(processor).processNext()
        verify(future).cancel(false)
    }

    @Test
    fun `worker stays stopped when scheduler does not schedule the poll`() {
        val worker = FsmTaskWorker(emptyList(), scheduler, properties)

        assertThatThrownBy { worker.start() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("TaskScheduler did not schedule the FSM task worker")

        assertThat(worker.isRunning).isFalse()
    }

    @Suppress("UNCHECKED_CAST")
    private fun processor(): FsmTaskProcessor<Any> = mock(FsmTaskProcessor::class.java) as FsmTaskProcessor<Any>
}
