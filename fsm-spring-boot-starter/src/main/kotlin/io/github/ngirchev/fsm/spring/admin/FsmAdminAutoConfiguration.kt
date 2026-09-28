package io.github.ngirchev.fsm.spring.admin

import io.github.ngirchev.fsm.Action
import io.github.ngirchev.fsm.Guard
import io.github.ngirchev.fsm.StateChangeListener
import io.github.ngirchev.fsm.spring.FsmAutoConfiguration
import io.github.ngirchev.fsm.spring.definition.FlowService
import io.github.ngirchev.fsm.spring.definition.FlowStore
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import java.util.function.Consumer
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.ApplicationContext
import org.springframework.core.env.Environment
import org.springframework.util.ClassUtils
import org.springframework.web.servlet.DispatcherServlet
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@AutoConfiguration(after = [FsmAutoConfiguration::class])
@ConditionalOnClass(DispatcherServlet::class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "fsm.admin", name = ["enabled"], havingValue = "true")
class FsmAdminAutoConfiguration {
    @Bean
    fun fsmAdminService(
        registrations: List<FsmAdminRegistration>, flows: ObjectProvider<FlowService>, stores: ObjectProvider<FlowStore>,
        guards: Map<String, Guard<*>>, actions: Map<String, Action<*>>,
        stateListeners: Map<String, StateChangeListener<*>>, completionListeners: Map<String, Consumer<*>>,
        beanFactory: ConfigurableListableBeanFactory,
    ): FsmAdminService {
        val store = checkNotNull(stores.ifAvailable) { "fsm.admin.enabled=true requires an application FlowStore bean" }
        return FsmAdminService(registrations, flows.getObject(), store, guards, actions,
            stateListeners, completionListeners, beanFactory)
    }

    @Bean
    fun fsmAdminController(admin: FsmAdminService, flows: FlowService, context: ApplicationContext) =
        FsmAdminController(admin, flows,
            ClassUtils.isPresent("org.springframework.security.web.csrf.CsrfToken", context.classLoader))

    @Bean
    fun fsmAdminExceptionHandler() = FsmAdminExceptionHandler()

    @Bean
    fun fsmAdminResources(environment: Environment): WebMvcConfigurer {
        val path = environment.getProperty("fsm.admin.base-path", "/fsm-admin")
        require(Regex("(/[A-Za-z0-9_-]+)+").matches(path)) {
            "fsm.admin.base-path must contain slash-separated path segments without a trailing slash"
        }
        return object : WebMvcConfigurer {
            override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
                registry.addResourceHandler("$path/ui/**").addResourceLocations("classpath:/fsm-admin/panel/ui/")
                registry.addResourceHandler("$path/editor/**").addResourceLocations("classpath:/fsm-admin/editor/")
            }
        }
    }
}
