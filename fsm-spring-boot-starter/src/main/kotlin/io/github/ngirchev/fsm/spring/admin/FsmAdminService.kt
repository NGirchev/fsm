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
    private val adminFlows: FsmAdminFlows,
    private val flows: FlowService,
    private val store: FlowStore,
    private val guards: Map<String, Guard<*>>,
    private val actions: Map<String, Action<*>>,
    private val stateListeners: Map<String, StateChangeListener<*>>,
    private val completionListeners: Map<String, Consumer<*>>,
    private val beanFactory: ConfigurableListableBeanFactory,
) {
    open fun registration(key: String): FsmAdminRegistration =
        adminFlows.find(key) ?: throw NoSuchElementException("FSM is not registered: $key")

    open fun list(): List<Map<String, Any?>> = adminFlows.all().map(::flow)

    /** HTTP representation of a flow; an application subclass may add its own properties. */
    open fun flow(registration: FsmAdminRegistration): Map<String, Any?> = mapOf(
        "flowKey" to registration.flowKey, "title" to registration.title, "kind" to registration.kind,
        "description" to registration.description, "deletable" to registration.deletable)

    @Transactional
    open fun createFlow(flowKey: String?, title: String?, kind: String?, description: String?, copyOf: String?): Map<String, Any?> {
        val key = text(flowKey, "flowKey")
        require(FLOW_KEY.matches(key)) { "flowKey must contain 1-120 letters, digits, dots, underscores or hyphens" }
        val newTitle = text(title, "title")
        val newKind = text(kind, "kind")
        val sourceKey = copyOf?.takeIf { it.isNotBlank() }
        val source = sourceKey?.let(::registration)
        store.lock(key)
        check(adminFlows.find(key) == null) { "FSM is already registered: $key" }
        val registration = adminFlows.create(key, newTitle, newKind, description, sourceKey)
        // A copy starts from what runs now; a flow that was never published gives its newest version.
        val copied = if (source == null) null else store.active(source.flowKey).or { store.latest(source.flowKey) }.orElse(null)
        val draft = copied?.definition ?: registration.initialDefinition
        registration.validateDraft.accept(draft)
        flows.createDraft(key, draft)
        return flow(registration)
    }

    @Transactional
    open fun renameFlow(key: String, title: String?, description: String?): Map<String, Any?> {
        lockedRegistration(key)
        return flow(adminFlows.rename(key, text(title, "title"), description))
    }

    @Transactional
    open fun deleteFlow(key: String) {
        lockedRegistration(key)
        adminFlows.delete(key)
        flows.deleteFlow(key)
    }

    /** Looks the flow up under its lock, so a change cannot interleave with the deletion of the flow. */
    private fun lockedRegistration(key: String): FsmAdminRegistration {
        store.lock(key)
        return registration(key)
    }

    private fun text(value: String?, name: String): String {
        require(!value.isNullOrBlank()) { "$name must not be blank" }
        return value
    }

    open fun behaviors(key: String): List<Map<String, String>> {
        registration(key)
        return describe(guards.keys, "guard") + describe(actions.keys, "action") +
            describe(stateListeners.keys, "stateListener") + describe(completionListeners.keys, "completionListener")
    }

    private fun describe(names: Set<String>, kind: String): List<Map<String, String>> = names.sorted().map { name ->
        val description = if (beanFactory.containsBeanDefinition(name)) {
            beanFactory.getBeanDefinition(name).description
        } else null
        val flowKind = beanFactory.findAnnotationOnBean(name, FsmFlowKind::class.java)?.value ?: OTHER_FLOW_KIND
        mapOf("id" to name, "kind" to kind, "flowKind" to flowKind, "description" to (description ?: name))
    }

    @Transactional
    open fun create(key: String, definition: FlowDefinition?): FlowVersion {
        val registration = lockedRegistration(key)
        val draft = definition ?: registration.initialDefinition
        registration.validateDraft.accept(draft)
        return flows.createDraft(key, draft)
    }

    @Transactional
    open fun update(key: String, version: Int, definition: FlowDefinition): FlowVersion {
        lockedRegistration(key).validateDraft.accept(definition)
        return flows.updateDraft(key, version, definition)
    }

    @Transactional
    open fun activate(key: String, version: Int, publish: Boolean): FlowVersion {
        // Validate the same version that FlowService activates, under its transaction-scoped lock.
        val registration = lockedRegistration(key)
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
