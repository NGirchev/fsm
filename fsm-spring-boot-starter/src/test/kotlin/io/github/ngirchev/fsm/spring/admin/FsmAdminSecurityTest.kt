package io.github.ngirchev.fsm.spring.admin

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class FsmAdminSecurityTest {
    @Test
    fun `host security protects UI resources and API with session csrf`() {
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
                listOf("/fsm-admin/", "/fsm-admin/ui/admin.js", "/fsm-admin/editor/index.html",
                    "/fsm-admin/api/flows", "/fsm-admin/api/csrf").forEach { path ->
                    mvc.perform(get(path)).andExpect(status().isUnauthorized)
                    mvc.perform(get(path).with(user("reader").roles("USER"))).andExpect(status().isForbidden)
                    mvc.perform(get(path).with(user("editor").roles("EDITOR"))).andExpect(status().isOk)
                }
                val session = MockHttpSession()
                val tokenResult = mvc.perform(get("/fsm-admin/api/csrf").session(session).with(user("editor").roles("EDITOR")))
                    .andExpect(header().string("Cache-Control", "no-store")).andReturn()
                val token = jacksonObjectMapper().readTree(tokenResult.response.contentAsString)
                val path = "/fsm-admin/api/flows/sample/versions"
                mvc.perform(post(path).session(session).with(user("editor").roles("EDITOR"))).andExpect(status().isForbidden)
                mvc.perform(post(path).session(session).with(user("editor").roles("EDITOR"))
                    .header(token["headerName"].asText(), "wrong")).andExpect(status().isForbidden)
                mvc.perform(post(path).session(session).with(user("reader").roles("USER"))
                    .header(token["headerName"].asText(), token["token"].asText())).andExpect(status().isForbidden)
                mvc.perform(post(path).session(session).with(user("editor").roles("EDITOR"))
                    .header(token["headerName"].asText(), token["token"].asText())).andExpect(status().isCreated)
            }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    class HostSecurity {
        @Bean
        fun hostSecurity(http: HttpSecurity): SecurityFilterChain = http
            .authorizeHttpRequests { it.requestMatchers("/fsm-admin/**").hasRole("EDITOR").anyRequest().denyAll() }
            .httpBasic { }
            .headers { it.frameOptions { frames -> frames.sameOrigin() } }
            .build()
    }
}
