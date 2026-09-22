package io.github.ngirchev.fsm.spring.worker

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

@AutoConfiguration
@ConditionalOnBean(FsmTaskProcessor::class)
@ConditionalOnProperty(prefix = "fsm.tasks", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(FsmTaskWorkerProperties::class)
class FsmTaskAutoConfiguration {
    @Bean("fsmTaskScheduler")
    @ConditionalOnMissingBean(name = ["fsmTaskScheduler"])
    fun fsmTaskScheduler(): ThreadPoolTaskScheduler = ThreadPoolTaskScheduler().apply {
        poolSize = 1
        setThreadNamePrefix("fsm-task-")
        setRemoveOnCancelPolicy(true)
        setWaitForTasksToCompleteOnShutdown(true)
    }

    @Bean
    @ConditionalOnMissingBean
    fun fsmTaskWorker(
        processors: List<FsmTaskProcessor<*>>,
        @Qualifier("fsmTaskScheduler") scheduler: TaskScheduler,
        properties: FsmTaskWorkerProperties,
    ): FsmTaskWorker = FsmTaskWorker(processors, scheduler, properties)
}
