package io.github.ngirchev.fsm.spring.worker

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.TaskScheduler
import java.time.Duration
import java.util.Optional

class FsmTaskAutoConfigurationTest {
    private val runner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(FsmTaskAutoConfiguration::class.java))
        .withUserConfiguration(TaskProcessorConfiguration::class.java)

    @Test
    fun `creates worker and dedicated scheduler when a processor exists`() {
        runner.run { context ->
            assertThat(context).hasSingleBean(FsmTaskWorker::class.java)
            assertThat(context).hasBean("fsmTaskScheduler")
            val properties = context.getBean(FsmTaskWorkerProperties::class.java)
            assertThat(properties.pollInterval).isEqualTo(Duration.ofSeconds(5))
            assertThat(properties.maxTasksPerProcessorPerPoll).isEqualTo(100)
        }
    }

    @Test
    fun `binds worker settings`() {
        runner.withPropertyValues(
            "fsm.tasks.poll-interval=250ms",
            "fsm.tasks.max-tasks-per-processor-per-poll=7",
        ).run { context ->
            val properties = context.getBean(FsmTaskWorkerProperties::class.java)
            assertThat(properties.pollInterval).isEqualTo(Duration.ofMillis(250))
            assertThat(properties.maxTasksPerProcessorPerPoll).isEqualTo(7)
        }
    }

    @Test
    fun `can disable automatic task processing`() {
        runner.withPropertyValues("fsm.tasks.enabled=false").run { context ->
            assertThat(context).doesNotHaveBean(FsmTaskWorker::class.java)
            assertThat(context).doesNotHaveBean("fsmTaskScheduler")
        }
    }

    @Test
    fun `does nothing without a task processor`() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FsmTaskAutoConfiguration::class.java))
            .run { context ->
                assertThat(context).doesNotHaveBean(FsmTaskWorker::class.java)
                assertThat(context).doesNotHaveBean("fsmTaskScheduler")
            }
    }

    @Configuration(proxyBeanMethods = false)
    class TaskProcessorConfiguration {
        @Bean
        fun taskProcessor(): FsmTaskProcessor<String> = FsmTaskProcessor(
            object : FsmTaskStore<String> {
                override fun claimNextPending(): Optional<String> = Optional.empty()
                override fun complete(task: String) = Unit
            },
            FsmTaskHandler { },
        )
    }
}
