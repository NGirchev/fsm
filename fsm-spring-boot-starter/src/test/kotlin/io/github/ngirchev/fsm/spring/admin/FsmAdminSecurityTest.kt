package io.github.ngirchev.fsm.spring.admin

import io.github.ngirchev.fsm.serialization.FsmDto
import io.github.ngirchev.fsm.spring.FsmAutoConfiguration
import io.github.ngirchev.fsm.spring.definition.FlowDefinition
import io.github.ngirchev.fsm.spring.definition.FlowStore
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class FsmAdminSecurityTest {
    @Test
    fun `host security and its CSRF protection apply to the API`() {
        WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration::class.java,
            HttpMessageConvertersAutoConfiguration::class.java, WebMvcAutoConfiguration::class.java,
            FsmAutoConfiguration::class.java, FsmAdminAutoConfiguration::class.java))
            .withPropertyValues("fsm.admin.enabled=true")
            .withUserConfiguration(HostSecurity::class.java)
            .withBean(FlowStore::class.java, { FsmAdminTest.MemoryStore() })
            .withBean(FsmAdminRegistration::class.java, { FsmAdminRegistration("sample", "Sample",
                FlowDefinition("NEW", FsmDto(false, mapOf("NEW" to emptyList()))), {}, {}) })
            .run { context ->
                val mvc = MockMvcBuilders.webAppContextSetup(context).apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity()).build()
                val flows = "/fsm-admin/api/flows"
                mvc.perform(get(flows)).andExpect(status().isUnauthorized)
                mvc.perform(get(flows).with(user("reader").roles("USER"))).andExpect(status().isForbidden)
                mvc.perform(get(flows).with(user("editor").roles("EDITOR"))).andExpect(status().isOk)
                // The starter exposes no CSRF endpoint; the host's own CSRF rules still apply to mutations.
                mvc.perform(get("/fsm-admin/api/csrf").with(user("editor").roles("EDITOR"))).andExpect(status().isNotFound)
                val path = "/fsm-admin/api/flows/sample/versions"
                mvc.perform(post(path).with(user("editor").roles("EDITOR"))).andExpect(status().isForbidden)
                mvc.perform(post(path).with(user("reader").roles("USER")).with(csrf())).andExpect(status().isForbidden)
                mvc.perform(post(path).with(user("editor").roles("EDITOR")).with(csrf())).andExpect(status().isCreated)
            }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    class HostSecurity {
        @Bean
        fun hostSecurity(http: HttpSecurity): SecurityFilterChain = http
            .authorizeHttpRequests { it.requestMatchers("/fsm-admin/**").hasRole("EDITOR").anyRequest().denyAll() }
            .httpBasic { }
            .build()
    }
}
