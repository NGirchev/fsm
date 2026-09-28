package io.github.ngirchev.fsm.spring.worker

import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import org.springframework.scheduling.TaskScheduler
import java.time.Duration
import java.util.concurrent.ScheduledFuture

class FsmTaskWorker(
    processors: List<FsmTaskProcessor<*>>,
    private val scheduler: TaskScheduler,
    private val properties: FsmTaskWorkerProperties,
) : SmartLifecycle {
    private val processors = processors.toList()
    private var scheduledTask: ScheduledFuture<*>? = null

    init {
        require(properties.pollInterval > Duration.ZERO) {
            "fsm.tasks.poll-interval must be positive"
        }
        require(properties.maxTasksPerProcessorPerPoll > 0) {
            "fsm.tasks.max-tasks-per-processor-per-poll must be positive"
        }
    }

    fun runOnce() {
        for (processor in processors) {
            try {
                processAvailableTasks(processor)
            } catch (exception: Exception) {
                logger.error("FSM task processing failed; the processor will be retried on the next poll", exception)
            }
        }
    }

    private fun processAvailableTasks(processor: FsmTaskProcessor<*>) {
        repeat(properties.maxTasksPerProcessorPerPoll) {
            if (!processor.processNext()) return
        }
    }

    @Synchronized
    override fun start() {
        if (scheduledTask == null) {
            scheduledTask = checkNotNull(scheduler.scheduleWithFixedDelay(this::runOnce, properties.pollInterval)) {
                "TaskScheduler did not schedule the FSM task worker"
            }
        }
    }

    @Synchronized
    override fun stop() {
        val task = scheduledTask ?: return
        task.cancel(false)
        scheduledTask = null
    }

    @Synchronized
    override fun isRunning(): Boolean = scheduledTask != null

    private companion object {
        private val logger = LoggerFactory.getLogger(FsmTaskWorker::class.java)
    }
}
