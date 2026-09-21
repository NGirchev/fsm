package io.github.ngirchev.fsm.spring.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.TaskScheduler;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;

public class FsmTaskWorker implements SmartLifecycle {
    private static final Logger LOGGER = LoggerFactory.getLogger(FsmTaskWorker.class);

    private final List<FsmTaskProcessor<?>> processors;
    private final TaskScheduler scheduler;
    private final FsmTaskWorkerProperties properties;
    private volatile ScheduledFuture<?> scheduledTask;

    public FsmTaskWorker(
            List<FsmTaskProcessor<?>> processors,
            TaskScheduler scheduler,
            FsmTaskWorkerProperties properties
    ) {
        if (properties.getPollInterval().compareTo(java.time.Duration.ZERO) <= 0) {
            throw new IllegalArgumentException("fsm.tasks.poll-interval must be positive");
        }
        if (properties.getMaxTasksPerProcessorPerPoll() <= 0) {
            throw new IllegalArgumentException("fsm.tasks.max-tasks-per-processor-per-poll must be positive");
        }
        this.processors = List.copyOf(processors);
        this.scheduler = scheduler;
        this.properties = properties;
    }

    public void runOnce() {
        for (FsmTaskProcessor<?> processor : processors) {
            processAvailableTasks(processor);
        }
    }

    private void processAvailableTasks(FsmTaskProcessor<?> processor) {
        for (int processed = 0; processed < properties.getMaxTasksPerProcessorPerPoll(); processed++) {
            try {
                if (!processor.processNext()) {
                    return;
                }
            } catch (RuntimeException exception) {
                LOGGER.error("FSM task processing failed; the processor will be retried on the next poll", exception);
                return;
            }
        }
    }

    @Override
    public synchronized void start() {
        if (scheduledTask == null) {
            scheduledTask = Objects.requireNonNull(
                    scheduler.scheduleWithFixedDelay(this::runOnce, properties.getPollInterval()),
                    "TaskScheduler did not schedule the FSM task worker"
            );
        }
    }

    @Override
    public synchronized void stop() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTask = null;
        }
    }

    @Override
    public boolean isRunning() {
        return scheduledTask != null;
    }
}
