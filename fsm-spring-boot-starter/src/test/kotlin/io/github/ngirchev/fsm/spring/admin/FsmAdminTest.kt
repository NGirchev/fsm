package io.github.ngirchev.fsm.spring.admin

import tools.jackson.module.kotlin.jacksonObjectMapper
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
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.http.MediaType
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.test.json.JsonCompareMode
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
    fun `custom prefix serves only the API and works without security`() {
        enabled().withPropertyValues("fsm.admin.base-path=/management/flows")
            .withClassLoader(FilteredClassLoader("org.springframework.security")).run { context ->
                val mvc = MockMvcBuilders.webAppContextSetup(context).build()
                mvc.perform(get("/management/flows/api/flows")).andExpect(status().isOk)
                listOf("/management/flows", "/management/flows/", "/management/flows/ui/admin.js",
                    "/management/flows/editor/index.html", "/management/flows/api/csrf", "/fsm-admin/api/flows").forEach {
                    mvc.perform(get(it)).andExpect { result -> assertThat(result.response.status).describedAs(it).isEqualTo(404) }
                }
            }
    }

    @Test
    fun `registered flows support first draft lifecycle and reject unregistered keys`() {
        enabled().withBean("second", FsmAdminRegistration::class.java, { registration("second") }).run { context ->
            val mvc = MockMvcBuilders.webAppContextSetup(context).build()
            mvc.perform(get("/fsm-admin/api/flows")).andExpect(status().isOk).andExpect(content().json("""[
                {"flowKey":"sample","title":"Sample","kind":"other","description":null,"deletable":false},
                {"flowKey":"second","title":"Sample","kind":"other","description":null,"deletable":false}
            ]""", JsonCompareMode.STRICT))
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
                // The kind comes from the bean method or the bean class; it never restricts publication.
                val expected = """[
                    {"id":"guard","kind":"guard","flowKind":"agent","description":"Available guard"},
                    {"id":"otherGuard","kind":"guard","flowKind":"other","description":"otherGuard"},
                    {"id":"singletonGuard","kind":"guard","flowKind":"other","description":"singletonGuard"},
                    {"id":"action","kind":"action","flowKind":"document","description":"action"},
                    {"id":"listener","kind":"stateListener","flowKind":"other","description":"listener"},
                    {"id":"completion","kind":"completionListener","flowKind":"other","description":"completion"}
                ]"""
                listOf("sample", "second").forEach { key ->
                    mvc.perform(get("/fsm-admin/api/flows/$key/behaviors"))
                        .andExpect(status().isOk).andExpect(content().json(expected, JsonCompareMode.STRICT))
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
        @FsmFlowKind("agent")
        fun guard() = Guard<StateContext<String>> { true }
        @Bean
        fun otherGuard() = Guard<Int> { it > 0 }
        @Bean
        fun action(): Action<StateContext<String>> = DocumentAction()
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

    @FsmFlowKind("document")
    class DocumentAction : Action<StateContext<String>> {
        override fun invoke(context: StateContext<String>) = Unit
    }

    @Test
    fun `fixed registrations refuse flow creation renaming and deletion`() {
        enabled().run { context ->
            val mvc = MockMvcBuilders.webAppContextSetup(context).build()
            val body = """{"flowKey":"created","title":"Created","kind":"agent"}"""
            mvc.perform(post("/fsm-admin/api/flows").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isMethodNotAllowed)
                .andExpect(jsonPath("$.message").value("This application does not support creating flows"))
            mvc.perform(put("/fsm-admin/api/flows/sample").contentType(MediaType.APPLICATION_JSON).content("""{"title":"Renamed"}"""))
                .andExpect(status().isMethodNotAllowed)
                .andExpect(jsonPath("$.message").value("This application does not support renaming flows"))
            mvc.perform(delete("/fsm-admin/api/flows/sample")).andExpect(status().isMethodNotAllowed)
                .andExpect(jsonPath("$.message").value("This application does not support deleting flows"))
            mvc.perform(get("/fsm-admin/api/flows")).andExpect(jsonPath("$.length()").value(1))
        }
    }

    @Test
    fun `application flow source creates copies renames and deletes flows per request`() {
        val store = MemoryStore()
        val flows = MemoryFlows(definition)
        val json = MediaType.APPLICATION_JSON
        runner.withPropertyValues("fsm.admin.enabled=true").withBean(FlowStore::class.java, { store })
            .withBean(FsmAdminFlows::class.java, { flows }).run { context ->
                val mvc = MockMvcBuilders.webAppContextSetup(context).build()
                val api = "/fsm-admin/api/flows"
                mvc.perform(get(api)).andExpect(content().json("[]"))
                mvc.perform(post(api).contentType(json).content(
                    """{"flowKey":"first","title":"First","kind":"agent","description":"Made here"}"""))
                    .andExpect(status().isCreated).andExpect(content().json(
                        """{"flowKey":"first","title":"First","kind":"agent","description":"Made here","deletable":true}""",
                        JsonCompareMode.STRICT))
                // The first draft is the registration template and the flow is visible without a restart.
                mvc.perform(get("$api/first/versions")).andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].status").value("DRAFT")).andExpect(jsonPath("$[0].definition.initialState").value("NEW"))
                mvc.perform(get(api)).andExpect(jsonPath("$[0].flowKey").value("first"))

                // A copy takes the active version, else the newest one, else the template.
                val edited = mapper.writeValueAsBytes(definition.copy(initialState = "EDITED",
                    table = FsmDto(false, mapOf("EDITED" to emptyList()))))
                mvc.perform(put("$api/first/versions/1").contentType(json).content(edited)).andExpect(status().isOk)
                fun copy(key: String, source: String) = mvc.perform(post(api).contentType(json).content(
                    """{"flowKey":"$key","title":"Copy","kind":"agent","copyOf":"$source"}"""))
                copy("fromDraft", "first").andExpect(status().isCreated)
                mvc.perform(get("$api/fromDraft/versions/1")).andExpect(jsonPath("$.definition.initialState").value("EDITED"))
                mvc.perform(post("$api/first/versions/1/publish")).andExpect(status().isOk)
                mvc.perform(post("$api/first/versions")).andExpect(status().isCreated)
                copy("fromActive", "first").andExpect(status().isCreated)
                mvc.perform(get("$api/fromActive/versions/1")).andExpect(jsonPath("$.definition.initialState").value("EDITED"))
                // copyVersion copies the version open in the editor, here the template draft v2 instead of active v1.
                fun copyVersion(key: String, version: String) = mvc.perform(post(api).contentType(json).content(
                    """{"flowKey":"$key","title":"Copy","kind":"agent","copyOf":"first","copyVersion":$version}"""))
                copyVersion("fromVersion", "2").andExpect(status().isCreated)
                mvc.perform(get("$api/fromVersion/versions/1")).andExpect(jsonPath("$.definition.initialState").value("NEW"))
                copyVersion("fromMissingVersion", "9").andExpect(status().isNotFound)
                flows.flows["empty"] = flows.registration("empty", "Empty", "agent", null)
                copy("fromEmpty", "empty").andExpect(status().isCreated)
                mvc.perform(get("$api/fromEmpty/versions/1")).andExpect(jsonPath("$.definition.initialState").value("NEW"))

                copy("fromMissing", "missing").andExpect(status().isNotFound)
                // A blank copyOf is the same as none: the template becomes the first draft.
                copy("fromBlank", " ").andExpect(status().isCreated)
                mvc.perform(get("$api/fromBlank/versions/1")).andExpect(jsonPath("$.definition.initialState").value("NEW"))
                mvc.perform(post(api).contentType(json).content("""{"flowKey":"first","title":"Again","kind":"agent"}"""))
                    .andExpect(status().isConflict).andExpect(jsonPath("$.message").value("FSM is already registered: first"))
                mvc.perform(post(api).contentType(json).content("""{"flowKey":"other","title":"Other","kind":"request"}"""))
                    .andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("Only agent flows can be created"))
                listOf("""{"title":"T","kind":"agent"}""" to "flowKey must not be blank",
                    """{"flowKey":"bad/key","title":"T","kind":"agent"}""" to
                        "flowKey must contain 1-120 letters, digits, dots, underscores or hyphens, not only dots",
                    """{"flowKey":"..","title":"T","kind":"agent"}""" to
                        "flowKey must contain 1-120 letters, digits, dots, underscores or hyphens, not only dots",
                    """{"flowKey":".","title":"T","kind":"agent"}""" to
                        "flowKey must contain 1-120 letters, digits, dots, underscores or hyphens, not only dots",
                    """{"flowKey":"k","title":"T","kind":"agent","copyVersion":1}""" to "copyVersion requires copyOf",
                    """{"flowKey":"k","title":" ","kind":"agent"}""" to "title must not be blank",
                    """{"flowKey":"k","title":"T"}""" to "kind must not be blank").forEach { (body, message) ->
                    mvc.perform(post(api).contentType(json).content(body))
                        .andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value(message))
                }
                assertThat(flows.flows.keys).containsExactly("first", "fromDraft", "fromActive", "fromVersion", "empty", "fromEmpty", "fromBlank")

                mvc.perform(put("$api/first").contentType(json).content("""{"title":"Renamed","description":"New text"}"""))
                    .andExpect(status().isOk).andExpect(jsonPath("$.title").value("Renamed"))
                    .andExpect(jsonPath("$.description").value("New text")).andExpect(jsonPath("$.kind").value("agent"))
                mvc.perform(put("$api/first").contentType(json).content("""{"description":"No title"}"""))
                    .andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("title must not be blank"))
                mvc.perform(put("$api/missing").contentType(json).content("""{"title":"T"}""")).andExpect(status().isNotFound)

                // The application may veto; otherwise every version goes, the active one included.
                flows.flows["builtin"] = flows.registration("builtin", "Built-in", "request", null)
                mvc.perform(delete("$api/builtin")).andExpect(status().isConflict)
                    .andExpect(jsonPath("$.message").value("Built-in flows cannot be deleted"))
                mvc.perform(delete("$api/missing")).andExpect(status().isNotFound)
                assertThat(store.list("first")).hasSize(2)
                mvc.perform(delete("$api/first")).andExpect(status().isNoContent)
                assertThat(store.list("first")).isEmpty()
                assertThat(store.list("fromActive")).hasSize(1)
                mvc.perform(get("$api/first/versions")).andExpect(status().isNotFound)

                store.supportsFlowDeletion = false
                mvc.perform(delete("$api/fromActive")).andExpect(status().isMethodNotAllowed)
                    .andExpect(jsonPath("$.message").value("This flow store does not support deleting flows"))
                assertThat(store.list("fromActive")).hasSize(1)
            }
    }

    @Test
    fun `changes of a flow look it up under the flow-key lock`() {
        val store = MemoryStore()
        val lookups = mutableListOf<Boolean>()
        val flows = object : MemoryFlows(definition) {
            override fun find(flowKey: String) = super.find(flowKey).also { lookups += store.locked }
        }
        flows.flows["sample"] = flows.registration("sample", "Sample", "agent", null)
        val json = MediaType.APPLICATION_JSON
        runner.withPropertyValues("fsm.admin.enabled=true").withBean(FlowStore::class.java, { store })
            .withBean(FsmAdminFlows::class.java, { flows }).run { context ->
                val mvc = MockMvcBuilders.webAppContextSetup(context).build()
                val api = "/fsm-admin/api/flows/sample"
                // A lookup before the lock could see a flow that a concurrent deletion is removing.
                listOf(post("$api/versions"), put("$api/versions/1").contentType(json).content(mapper.writeValueAsBytes(definition)),
                    post("$api/versions/1/publish"), put(api).contentType(json).content("""{"title":"Renamed"}"""),
                    delete(api)).forEach { request ->
                    store.locked = false
                    lookups.clear()
                    mvc.perform(request).andExpect(status().is2xxSuccessful)
                    assertThat(lookups).isNotEmpty.containsOnly(true)
                }
            }
    }

    @Test
    fun `application beans replace the default admin service controller and error handler`() {
        val handler = FsmAdminExceptionHandler()
        enabled().withUserConfiguration(CustomAdmin::class.java)
            .withBean(FsmAdminExceptionHandler::class.java, { handler }).run { context ->
                assertThat(context).hasSingleBean(FsmAdminService::class.java).hasSingleBean(FsmAdminController::class.java)
                assertThat(context.getBean(FsmAdminExceptionHandler::class.java)).isSameAs(handler)
                assertThat(context.getBean(FsmAdminController::class.java)).isSameAs(context.getBean(CustomAdmin::class.java).controller)
                MockMvcBuilders.webAppContextSetup(context).build().perform(get("/fsm-admin/api/flows"))
                    .andExpect(jsonPath("$[0].flowKey").value("sample")).andExpect(jsonPath("$[0].defaultAgent").value(false))
            }
    }

    @Configuration(proxyBeanMethods = false)
    class CustomAdmin {
        lateinit var controller: FsmAdminController

        @Bean
        fun fsmAdminService(adminFlows: FsmAdminFlows, flows: FlowService, store: FlowStore,
            beanFactory: ConfigurableListableBeanFactory): FsmAdminService =
            object : FsmAdminService(adminFlows, flows, store, emptyMap(), emptyMap(), emptyMap(), emptyMap(), beanFactory) {
                override fun flow(registration: FsmAdminRegistration) = super.flow(registration) + ("defaultAgent" to false)
            }

        @Bean
        fun fsmAdminController(admin: FsmAdminService, flows: FlowService): FsmAdminController =
            FsmAdminController(admin, flows).also { controller = it }
    }

    /** Flows kept by the application, as a host with run-time flow creation would provide them. */
    open class MemoryFlows(private val definition: FlowDefinition) : FsmAdminFlows {
        val flows = linkedMapOf<String, FsmAdminRegistration>()
        fun registration(key: String, title: String, kind: String, description: String?) =
            FsmAdminRegistration(key, title, definition, {}, {}, kind, description, true)
        override fun all() = flows.values.toList()
        override fun find(flowKey: String) = flows[flowKey]
        override fun create(flowKey: String, title: String, kind: String, description: String?, copyOf: String?): FsmAdminRegistration {
            require(kind == "agent") { "Only agent flows can be created" }
            return registration(flowKey, title, kind, description).also { flows[flowKey] = it }
        }
        override fun rename(flowKey: String, title: String, description: String?) =
            registration(flowKey, title, flows.getValue(flowKey).kind, description).also { flows[flowKey] = it }
        override fun delete(flowKey: String) {
            check(flowKey != "builtin") { "Built-in flows cannot be deleted" }
            flows.remove(flowKey)
        }
    }

    /** Test adapter only: production stores must hold locks and roll back within the caller transaction. */
    class MemoryStore : FlowStore {
        private val versions = mutableMapOf<Pair<String, Int>, FlowVersion>()
        var supportsFlowDeletion = true
        override fun deleteFlow(flowKey: String) {
            if (!supportsFlowDeletion) return super.deleteFlow(flowKey)
            versions.keys.removeIf { it.first == flowKey }
        }
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
