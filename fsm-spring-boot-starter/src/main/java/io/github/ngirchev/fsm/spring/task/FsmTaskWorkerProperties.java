package io.github.ngirchev.fsm.spring.task;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("fsm.tasks")
public class FsmTaskWorkerProperties {
    private Duration pollInterval = Duration.ofSeconds(5);
    private int maxTasksPerProcessorPerPoll = 100;

    public Duration getPollInterval() {
        return pollInterval;
    }

    public void setPollInterval(Duration pollInterval) {
        this.pollInterval = pollInterval;
    }

    public int getMaxTasksPerProcessorPerPoll() {
        return maxTasksPerProcessorPerPoll;
    }

    public void setMaxTasksPerProcessorPerPoll(int maxTasksPerProcessorPerPoll) {
        this.maxTasksPerProcessorPerPoll = maxTasksPerProcessorPerPoll;
    }
}
