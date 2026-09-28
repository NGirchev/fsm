package io.github.ngirchev.fsm.spring.admin

import io.github.ngirchev.fsm.Action
import io.github.ngirchev.fsm.Guard
import io.github.ngirchev.fsm.StateChangeListener
import io.github.ngirchev.fsm.spring.definition.FlowDefinition
import io.github.ngirchev.fsm.spring.definition.FlowService
import io.github.ngirchev.fsm.spring.definition.FlowStore
import io.github.ngirchev.fsm.spring.definition.FlowVersion
import org.springframework.transaction.annotation.Transactional
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import java.util.function.Consumer

/** HTTP-facing policy; version lifecycle and persistence remain in FlowService. */
@Transactional(readOnly = true)
open class FsmAdminService(
    registrations: List<FsmAdminRegistration>,
    private val flows: FlowService,
    private val store: FlowStore,
    private val guards: Map<String, Guard<*>>,
    private val actions: Map<String, Action<*>>,
    private val stateListeners: Map<String, StateChangeListener<*>>,
    private val completionListeners: Map<String, Consumer<*>>,
    private val beanFactory: ConfigurableListableBeanFactory,
) {
    private val registrations = registrations.associateBy { it.flowKey }

    init {
        require(this.registrations.size == registrations.size) { "Duplicate FSM admin flowKey" }
        registrations.forEach {
            require(Regex("[A-Za-z0-9._-]{1,120}").matches(it.flowKey)) { "Invalid FSM admin flowKey" }
            require(it.title.isNotBlank()) { "FSM admin title must not be blank" }
        }
    }

    open fun registration(key: String): FsmAdminRegistration =
        registrations[key] ?: throw NoSuchElementException("FSM is not registered: $key")

    open fun list(): List<Map<String, String>> = registrations.values.map { mapOf("flowKey" to it.flowKey, "title" to it.title) }

    open fun behaviors(key: String): List<Map<String, String>> {
        registration(key)
        return describe(guards.keys, "guard") + describe(actions.keys, "action") +
            describe(stateListeners.keys, "stateListener") + describe(completionListeners.keys, "completionListener")
    }

    private fun describe(names: Set<String>, kind: String): List<Map<String, String>> = names.sorted().map { name ->
        val description = if (beanFactory.containsBeanDefinition(name)) {
            beanFactory.getBeanDefinition(name).description
        } else null
        mapOf("id" to name, "kind" to kind, "description" to (description ?: name))
    }

    @Transactional
    open fun create(key: String, definition: FlowDefinition?): FlowVersion {
        val registration = registration(key)
        val draft = definition ?: registration.initialDefinition
        registration.validateDraft.accept(draft)
        return flows.createDraft(key, draft)
    }

    @Transactional
    open fun update(key: String, version: Int, definition: FlowDefinition): FlowVersion {
        registration(key).validateDraft.accept(definition)
        return flows.updateDraft(key, version, definition)
    }

    @Transactional
    open fun activate(key: String, version: Int, publish: Boolean): FlowVersion {
        val registration = registration(key)
        // Validate the same version that FlowService activates, under its transaction-scoped lock.
        store.lock(key)
        val definition = flows.get(key, version).definition
        registration.validateDraft.accept(definition)
        validateBehaviors(definition)
        registration.validateActivation.accept(definition)
        return if (publish) flows.publish(key, version) else flows.activate(key, version)
    }

    private fun validateBehaviors(definition: FlowDefinition) {
        fun validate(names: List<String>, beans: Map<String, *>, kind: String) {
            names.forEach { name ->
                require(name in beans) { "Unknown $kind bean: $name" }
            }
        }
        definition.table.transitions.values.flatten().forEach {
            validate(it.to.conditions, guards, "guard")
            validate(it.to.actions, actions, "action")
            validate(it.to.postActions, actions, "action")
        }
        validate(definition.execution.stateListeners, stateListeners, "stateListener")
        validate(definition.execution.completionListeners, completionListeners, "completionListener")
    }
}
