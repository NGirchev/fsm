package io.github.ngirchev.fsm.spring

import io.github.ngirchev.fsm.Action
import io.github.ngirchev.fsm.Guard
import io.github.ngirchev.fsm.StateContext
import io.github.ngirchev.fsm.To
import io.github.ngirchev.fsm.impl.extended.ExFsm
import io.github.ngirchev.fsm.impl.extended.ExTransitionTable
import io.github.ngirchev.fsm.exception.FsmEventSourcingTransitionFailedException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class FsmAutoConfigurationTest {
    private val runner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration::class.java, FsmAutoConfiguration::class.java))

    @Test
    fun `round trip preserves ordinary beans and invokes guards actions and post actions`() {
        runner.withUserConfiguration(Behaviors::class.java).run { context ->
            val registry = context.getBean(FsmBeanRegistry::class.java)
            val serializer = context.getBean(SpringFsmJsonSerializer::class.java)
            val table = ExTransitionTable.Builder<String, String>()
                .maxImmediateAutoTransitions(3)
                .add("NEW", "GO", To("DONE", registry.guard<String>("allowed"),
                    registry.action<String>("mark"), registry.action<String>("receipt")))
                .build()
            val json = serializer.serialize(table)
            assertThat(json).contains("allowed", "mark", "receipt")
            val restored = serializer.deserialize(json, { it }, { it })
            assertThat(serializer.toDto(restored)).isEqualTo(serializer.toDto(table))
            val behaviors = context.getBean(Behaviors::class.java)
            val blocked = ExFsm("NEW", restored)
            assertThatThrownBy { blocked.onEvent("GO") }
                .isInstanceOf(FsmEventSourcingTransitionFailedException::class.java)
            assertThat(blocked.getState()).isEqualTo("NEW")
            assertThat(behaviors.calls).isEmpty()
            behaviors.approved = true
            blocked.onEvent("GO")
            assertThat(blocked.getState()).isEqualTo("DONE")
            assertThat(behaviors.calls).containsExactly("mark", "receipt")
            assertThat(restored.transitions.getValue("NEW").single().to.actions.single())
                .isSameAs(context.getBean("mark"))
        }
    }

    @Test
    fun `unknown references fail for each handler kind`() {
        runner.run { context ->
            val serializer = context.getBean(SpringFsmJsonSerializer::class.java)
            listOf("conditions", "actions", "postActions").forEach { field ->
                val fields = listOf("conditions", "actions", "postActions").joinToString(",") {
                    "\"$it\":" + if (it == field) "[\"missing\"]" else "[]"
                }
                val json = """{"autoTransitionEnabled":false,"transitions":{"NEW":[{"from":"NEW","event":"GO","to":{"state":"DONE",$fields,"timeout":null}}]}}"""
                assertThatThrownBy { serializer.deserialize(json, { it }, { it }) }
                    .isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("missing")
            }
        }
    }

    @Test
    fun `unregistered and ambiguous handlers cannot be silently omitted`() {
        val action = Action<StateContext<String>> { }
        assertThatThrownBy { FsmBeanRegistry(emptyMap(), emptyMap()).actionName(action) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { FsmBeanRegistry(mapOf("one" to action, "two" to action), emptyMap()).actionName(action) }
            .isInstanceOf(IllegalArgumentException::class.java)
        runner.run { context ->
            val table = ExTransitionTable.Builder<String, String>()
                .add("NEW", "GO", "DONE", action = action).build()
            assertThatThrownBy { context.getBean(SpringFsmJsonSerializer::class.java).serialize(table) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Test
    fun `user supplied services replace defaults`() {
        val registry = FsmBeanRegistry(emptyMap(), emptyMap())
        val serializer = SpringFsmJsonSerializer(com.fasterxml.jackson.module.kotlin.jacksonObjectMapper(), registry)
        runner.withBean(FsmBeanRegistry::class.java, { registry })
            .withBean(SpringFsmJsonSerializer::class.java, { serializer }).run { context ->
                assertThat(context.getBean(FsmBeanRegistry::class.java)).isSameAs(registry)
                assertThat(context.getBean(SpringFsmJsonSerializer::class.java)).isSameAs(serializer)
            }
    }

    @Test
    fun `starter is discovered without explicit imports and supports no handlers`() {
        ApplicationContextRunner().withUserConfiguration(BootApplication::class.java).run { context ->
            assertThat(context).hasSingleBean(FsmBeanRegistry::class.java)
                .hasSingleBean(SpringFsmJsonSerializer::class.java)
            val table = ExTransitionTable.Builder<String, String>().add("NEW", "GO", "DONE").build()
            val serializer = context.getBean(SpringFsmJsonSerializer::class.java)
            assertThat(serializer.toDto(serializer.deserialize(serializer.serialize(table), { it }, { it })))
                .isEqualTo(serializer.toDto(table))
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    class BootApplication

    @Configuration(proxyBeanMethods = false)
    class Behaviors {
        var approved = false
        val calls = mutableListOf<String>()
        @Bean fun allowed(): Guard<StateContext<String>> = Guard { approved }
        @Bean
        @Suppress("UNCHECKED_CAST")
        fun mark(): Action<StateContext<String>> = org.springframework.aop.framework.ProxyFactory(
            Action<StateContext<String>> { calls.add("mark") },
        ).proxy as Action<StateContext<String>>
        @Bean fun receipt(): Action<StateContext<String>> = Action { calls.add("receipt") }
    }
}
