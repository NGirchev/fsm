package io.github.ngirchev.fsm.spring.admin

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.ngirchev.fsm.Action
import io.github.ngirchev.fsm.Guard
import io.github.ngirchev.fsm.StateContext
import io.github.ngirchev.fsm.StateChangeListener
import io.github.ngirchev.fsm.serialization.FsmDto
import io.github.ngirchev.fsm.spring.FsmAutoConfiguration
import io.github.ngirchev.fsm.spring.definition.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.http.MediaType
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Description
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional
import java.util.function.Consumer

class FsmAdminTest {
    private val definition = FlowDefinition("NEW", FsmDto(false, mapOf("NEW" to emptyList())))
    private val mapper = jacksonObjectMapper()
    private val configuration = AutoConfigurations.of(JacksonAutoConfiguration::class.java,
        HttpMessageConvertersAutoConfiguration::class.java, WebMvcAutoConfiguration::class.java,
        FsmAutoConfiguration::class.java, FsmAdminAutoConfiguration::class.java)
    private val runner = WebApplicationContextRunner().withInitializer {
        // MockServletContext otherwise treats the entire classpath as the servlet document root.
        it.servletContext = org.springframework.mock.web.MockServletContext("src/main/webapp",
            org.springframework.core.io.FileSystemResourceLoader())
    }.withConfiguration(configuration)
    private fun registration(key: String = "sample", title: String = "Sample") =
        FsmAdminRegistration(key, title, definition, {}, {})
    private fun enabled() = runner.withPropertyValues("fsm.admin.enabled=true")
        .withBean(FlowStore::class.java, { MemoryStore() })
        .withBean(FsmAdminRegistration::class.java, { registration() })

    @Test
    fun `disabled admin exposes neither controllers nor packaged resources`() {
        listOf("false", "").forEach { enabled ->
            runner.withPropertyValues("fsm.admin.enabled=$enabled").run { context ->
                assertThat(context).doesNotHaveBean(FsmAdminController::class.java)
                val mvc = MockMvcBuilders.webAppContextSetup(context).build()
                listOf("/fsm-admin/", "/fsm-admin/api/flows", "/fsm-admin/ui/admin.js",
                    "/fsm-admin/editor/index.html", "/fsm-admin/panel/index.html").forEach {
                    mvc.perform(get(it)).andExpect { result -> assertThat(result.response.status).describedAs(it).isEqualTo(404) }
                }
            }
        }
        runner.run { assertThat(it).doesNotHaveBean(FsmAdminService::class.java) }
    }

    @Test
    fun `ordinary nonweb application starts with enabled flag and no web or security classes`() {
        ApplicationContextRunner().withConfiguration(AutoConfigurations.of(
            JacksonAutoConfiguration::class.java, FsmAutoConfiguration::class.java, FsmAdminAutoConfiguration::class.java))
            .withClassLoader(FilteredClassLoader("org.springframework.web", "jakarta.servlet", "org.springframework.security"))
            .withPropertyValues("fsm.admin.enabled=true").run {
                assertThat(it).hasNotFailed().hasSingleBean(FlowLoader::class.java)
                    .doesNotHaveBean("fsmAdminController")
            }
    }

    @Test
    fun `enabled admin fails clearly without store with duplicate keys or invalid configuration`() {
        runner.withPropertyValues("fsm.admin.enabled=true").run {
            assertThat(it.startupFailure).hasRootCauseMessage("fsm.admin.enabled=true requires an application FlowStore bean")
        }
        enabled().withBean("duplicate", FsmAdminRegistration::class.java, { registration() }).run {
            assertThat(it.startupFailure).hasRootCauseMessage("Duplicate FSM admin flowKey")
        }
        listOf("/", "/fsm-admin/", "//admin", "/admin/../public", "admin").forEach { path ->
            enabled().withPropertyValues("fsm.admin.base-path=$path").run {
                assertThat(it.startupFailure).hasRootCauseMessage(
                    "fsm.admin.base-path must contain slash-separated path segments without a trailing slash")
            }
        }
        runner.withPropertyValues("fsm.admin.enabled=true").withBean(FlowStore::class.java, { MemoryStore() })
            .withBean(FsmAdminRegistration::class.java, { registration("bad/key") }).run {
                assertThat(it.startupFailure).hasRootCauseMessage("Invalid FSM admin flowKey")
            }
        runner.withPropertyValues("fsm.admin.enabled=true").withBean(FlowStore::class.java, { MemoryStore() })
            .withBean(FsmAdminRegistration::class.java, { registration(title = " ") }).run {
                assertThat(it.startupFailure).hasRootCauseMessage("FSM admin title must not be blank")
            }
    }

    @Test
    fun `custom prefix serves the packaged panel and editor and works without security`() {
        enabled().withPropertyValues("fsm.admin.base-path=/management/flows")
            .withClassLoader(FilteredClassLoader("org.springframework.security")).run { context ->
                val mvc = MockMvcBuilders.webAppContextSetup(context).build()
                mvc.perform(get("/test/management/flows").contextPath("/test"))
                    .andExpect(status().isFound).andExpect(header().string("Location", "/test/management/flows/"))
                mvc.perform(get("/management/flows/")).andExpect(status().isOk)
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("FSM administration")))
                mvc.perform(get("/management/flows/ui/admin.js")).andExpect(status().isOk)
                mvc.perform(get("/management/flows/editor/index.html")).andExpect(status().isOk)
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("FSM Visual Editor")))
                mvc.perform(get("/management/flows/api/csrf")).andExpect(status().isOk)
                    .andExpect(content().json("{}"))
                mvc.perform(get("/fsm-admin/api/flows")).andExpect(status().isNotFound)
                mvc.perform(get("/management/flows/ui/../editor/index.html")).andExpect(status().isNotFound)
            }
    }

    @Test
    fun `registered flows support first draft lifecycle and reject unregistered keys`() {
        enabled().withBean("second", FsmAdminRegistration::class.java, { registration("second") }).run { context ->
            val mvc = MockMvcBuilders.webAppContextSetup(context).build()
            mvc.perform(get("/fsm-admin/api/flows")).andExpect(status().isOk).andExpect(jsonPath("$.length()").value(2))
            mvc.perform(get("/fsm-admin/api/csrf")).andExpect(content().json("{}"))
            mvc.perform(get("/fsm-admin/api/flows/sample/behaviors")).andExpect(content().json("[]"))
            val path = "/fsm-admin/api/flows/sample/versions"
            mvc.perform(get(path)).andExpect(content().json("[]"))
            mvc.perform(post(path)).andExpect(status().isCreated).andExpect(jsonPath("$.definition.initialState").value("NEW"))
            mvc.perform(put("$path/1").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(definition)))
                .andExpect(status().isOk)
            mvc.perform(post("$path/1/publish")).andExpect(status().isOk).andExpect(jsonPath("$.status").value("ACTIVE"))
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(definition)))
                .andExpect(status().isCreated).andExpect(jsonPath("$.version").value(2))
            mvc.perform(post("$path/2/publish")).andExpect(status().isOk)
            mvc.perform(post("$path/1/activate")).andExpect(status().isOk).andExpect(jsonPath("$.status").value("ACTIVE"))
            mvc.perform(get("$path/2")).andExpect(jsonPath("$.status").value("ARCHIVED"))
            mvc.perform(post(path)).andExpect(status().isCreated)
            mvc.perform(delete("$path/3")).andExpect(status().isNoContent)
            mvc.perform(get("$path/3")).andExpect(status().isNotFound)
            mvc.perform(delete("$path/1")).andExpect(status().isConflict)
            mvc.perform(put("$path/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("Invalid flow definition JSON"))
            listOf(get("/fsm-admin/api/flows/unknown/versions"), get("/fsm-admin/api/flows/unknown/behaviors"),
                post("/fsm-admin/api/flows/unknown/versions"), post("/fsm-admin/api/flows/unknown/versions/1/publish"))
                .forEach { mvc.perform(it).andExpect(status().isNotFound) }
            mvc.perform(get("/fsm-admin/api/flows/second/versions")).andExpect(content().json("[]"))
        }
    }

    @Test
    fun `catalog discovers typed Spring beans and rejects missing or wrong kinds at publication`() {
        val allowed = definition.copy(table = FsmDto(false, mapOf("NEW" to listOf(
            io.github.ngirchev.fsm.serialization.TransitionDto("NEW",
                io.github.ngirchev.fsm.serialization.ToDto("DONE", listOf("guard"), listOf("action"), listOf("action"), null), "GO")),
            "DONE" to emptyList())), execution = FlowExecution(listOf("listener"), listOf("completion")))
        enabled().withUserConfiguration(HandlerBeans::class.java)
            .withBean("second", FsmAdminRegistration::class.java, { registration("second") })
            .withInitializer { it.beanFactory.registerSingleton("singletonGuard", Guard<String> { true }) }
            .run { context ->
                val mvc = MockMvcBuilders.webAppContextSetup(context).build()
                val expected = """[
                    {"id":"guard","kind":"guard","description":"Available guard"},
                    {"id":"otherGuard","kind":"guard","description":"otherGuard"},
                    {"id":"singletonGuard","kind":"guard","description":"singletonGuard"},
                    {"id":"action","kind":"action","description":"action"},
                    {"id":"listener","kind":"stateListener","description":"listener"},
                    {"id":"completion","kind":"completionListener","description":"completion"}
                ]"""
                listOf("sample", "second").forEach { key ->
                    mvc.perform(get("/fsm-admin/api/flows/$key/behaviors"))
                        .andExpect(status().isOk).andExpect(content().json(expected))
                }
                val admin = context.getBean(FsmAdminService::class.java)
                val draft = admin.create("sample", allowed)
                admin.activate("sample", draft.version, true)
                assertThat(context.getBean(FlowService::class.java).active("sample").definition).isEqualTo(allowed)

                val transition = allowed.table.transitions.getValue("NEW").single()
                val wrongTransitions = listOf(
                    transition.to.copy(conditions = listOf("action")),
                    transition.to.copy(actions = listOf("guard")),
                    transition.to.copy(postActions = listOf("missing")))
                val invalid = wrongTransitions.map { to ->
                    allowed.copy(table = FsmDto(false, mapOf("NEW" to listOf(transition.copy(to = to)), "DONE" to emptyList())))
                } + listOf(
                    allowed.copy(execution = FlowExecution(listOf("completion"), emptyList())),
                    allowed.copy(execution = FlowExecution(emptyList(), listOf("listener"))))
                invalid.forEach { bad ->
                    val rejected = admin.create("sample", bad)
                    org.assertj.core.api.Assertions.assertThatThrownBy { admin.activate("sample", rejected.version, true) }
                        .isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("Unknown")
                    assertThat(context.getBean(FlowService::class.java).get("sample", rejected.version).status)
                        .isEqualTo(FlowVersionStatus.DRAFT)
                }
            }
    }

    @Configuration(proxyBeanMethods = false)
    class HandlerBeans {
        @Bean
        @Description("Available guard")
        fun guard() = Guard<StateContext<String>> { true }
        @Bean
        fun otherGuard() = Guard<Int> { it > 0 }
        @Bean
        fun action() = Action<StateContext<String>> { }
        @Bean
        fun listener() = StateChangeListener<String> { _, _, _ -> }
        @Bean
        fun completion() = Consumer<StateContext<String>> { }
        @Bean
        fun unrelatedBean() = "not an FSM handler"
    }

    @Test
    fun `application validation rejects writes and activation without changing versions`() {
        val store = MemoryStore()
        val registration = FsmAdminRegistration("sample", "Sample", definition, {
            require(it.initialState != "BAD") { "Invalid domain event" }
        }, { require(it.initialState != "BLOCKED") { "Not executable by this application" } })
        runner.withPropertyValues("fsm.admin.enabled=true")
            .withBean(FlowStore::class.java, { store }).withBean(FsmAdminRegistration::class.java, { registration }).run { context ->
                val mvc = MockMvcBuilders.webAppContextSetup(context).build()
                val path = "/fsm-admin/api/flows/sample/versions"
                mvc.perform(post(path)).andExpect(status().isCreated)
                mvc.perform(post("$path/1/publish")).andExpect(status().isOk)
                val bad = mapper.writeValueAsBytes(definition.copy(initialState = "BAD"))
                mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(bad)).andExpect(status().isBadRequest)
                mvc.perform(put("$path/1").contentType(MediaType.APPLICATION_JSON).content(bad)).andExpect(status().isBadRequest)
                val blocked = mapper.writeValueAsBytes(definition.copy(initialState = "BLOCKED"))
                mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(blocked)).andExpect(status().isCreated)
                mvc.perform(post("$path/2/publish")).andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.message").value("Not executable by this application"))
                assertThat(store.active("sample").get().version).isEqualTo(1)
                assertThat(store.get("sample", 2).get().status).isEqualTo(FlowVersionStatus.DRAFT)
                assertThat(store.locked).isTrue()
            }
    }

    /** Test adapter only: production stores must hold locks and roll back within the caller transaction. */
    class MemoryStore : FlowStore {
        private val versions = mutableMapOf<Pair<String, Int>, FlowVersion>()
        var locked = false
        override fun lock(flowKey: String) { locked = true }
        override fun latest(flowKey: String) = Optional.ofNullable(list(flowKey).maxByOrNull { it.version })
        override fun get(flowKey: String, version: Int) = Optional.ofNullable(versions[flowKey to version])
        override fun active(flowKey: String) = Optional.ofNullable(list(flowKey).firstOrNull { it.status == FlowVersionStatus.ACTIVE })
        override fun list(flowKey: String) = versions.values.filter { it.flowKey == flowKey }
        override fun save(version: FlowVersion): FlowVersion {
            versions[version.flowKey to version.version] = version
            return version
        }
        override fun deleteDraft(flowKey: String, version: Int) { versions.remove(flowKey to version) }
    }
}
