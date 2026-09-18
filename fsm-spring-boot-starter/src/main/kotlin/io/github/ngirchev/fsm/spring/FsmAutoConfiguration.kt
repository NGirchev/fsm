package io.github.ngirchev.fsm.spring

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.ngirchev.fsm.Action
import io.github.ngirchev.fsm.AutoTransitionScheduler
import io.github.ngirchev.fsm.Guard
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.context.annotation.Bean

@AutoConfiguration(after = [JacksonAutoConfiguration::class])
class FsmAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun fsmBeanRegistry(
        actions: Map<String, Action<*>>,
        guards: Map<String, Guard<*>>,
        schedulers: Map<String, AutoTransitionScheduler<*>>,
    ): FsmBeanRegistry = FsmBeanRegistry(actions, guards, schedulers)

    @Bean
    @ConditionalOnMissingBean
    fun springFsmJsonSerializer(mapper: ObjectMapper, registry: FsmBeanRegistry): SpringFsmJsonSerializer =
        SpringFsmJsonSerializer(mapper, registry)
}
