package io.github.ngirchev.fsm.spring.task

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import org.springframework.scheduling.TaskScheduler
import java.time.Duration
import java.util.concurrent.ScheduledFuture

class FsmTaskWorkerTest {
    private val scheduler = mock<TaskScheduler>()
    private val properties = FsmTaskWorkerProperties().apply {
        pollInterval = Duration.ofMillis(250)
        maxTasksPerProcessorPerPoll = 2
    }

    @Test
    fun `properties expose defaults and accept overrides`() {
        val defaults = FsmTaskWorkerProperties()
        assertThat(defaults.pollInterval).isEqualTo(Duration.ofSeconds(5))
        assertThat(defaults.maxTasksPerProcessorPerPoll).isEqualTo(100)

        assertThat(properties.pollInterval).isEqualTo(Duration.ofMillis(250))
        assertThat(properties.maxTasksPerProcessorPerPoll).isEqualTo(2)
    }

    @Test
    fun `rejects non-positive worker settings`() {
        properties.pollInterval = Duration.ZERO
        assertThatThrownBy { FsmTaskWorker(emptyList(), scheduler, properties) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("fsm.tasks.poll-interval must be positive")

        properties.pollInterval = Duration.ofSeconds(1)
        properties.maxTasksPerProcessorPerPoll = 0
        assertThatThrownBy { FsmTaskWorker(emptyList(), scheduler, properties) }
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
    fun `lifecycle schedules only once and cancels on stop`() {
        @Suppress("UNCHECKED_CAST")
        val future = mock<ScheduledFuture<Any>>() as ScheduledFuture<*>
        `when`(scheduler.scheduleWithFixedDelay(any(Runnable::class.java), any(Duration::class.java)))
            .thenReturn(future)
        val worker = FsmTaskWorker(emptyList(), scheduler, properties)

        assertThat(worker.isRunning).isFalse()
        worker.start()
        worker.start()
        assertThat(worker.isRunning).isTrue()
        worker.stop()
        worker.stop()
        assertThat(worker.isRunning).isFalse()

        verify(scheduler).scheduleWithFixedDelay(any(Runnable::class.java), eq(Duration.ofMillis(250)))
        verify(future).cancel(false)
    }

    @Suppress("UNCHECKED_CAST")
    private fun processor(): FsmTaskProcessor<Any> = mock(FsmTaskProcessor::class.java) as FsmTaskProcessor<Any>
}
