package io.github.ngirchev.fsm.spring

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.ngirchev.fsm.Action
import io.github.ngirchev.fsm.Guard
import io.github.ngirchev.fsm.spring.definition.FlowLoader
import io.github.ngirchev.fsm.spring.definition.FlowService
import io.github.ngirchev.fsm.spring.definition.FlowStore
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
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
    ): FsmBeanRegistry = FsmBeanRegistry(actions, guards)

    @Bean
    @ConditionalOnMissingBean
    fun springFsmJsonSerializer(mapper: ObjectMapper, registry: FsmBeanRegistry): SpringFsmJsonSerializer =
        SpringFsmJsonSerializer(mapper, registry)

    @Bean
    @ConditionalOnMissingBean
    fun flowLoader(serializer: SpringFsmJsonSerializer): FlowLoader = FlowLoader(serializer)

    @Bean
    @ConditionalOnBean(FlowStore::class)
    @ConditionalOnMissingBean
    fun flowService(store: FlowStore, loader: FlowLoader): FlowService = FlowService(store, loader)
}
