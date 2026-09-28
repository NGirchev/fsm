package io.github.ngirchev.fsm.spring.worker

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import java.time.Duration

@ConfigurationProperties("fsm.tasks")
data class FsmTaskWorkerProperties(
    @param:DefaultValue("5s") val pollInterval: Duration,
    @param:DefaultValue("100") val maxTasksPerProcessorPerPoll: Int,
)
