package io.github.ngirchev.fsm.example.flow

import io.github.ngirchev.fsm.impl.extended.ExTransitionTable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.ConcurrentHashMap

@Service
class FlowService(
    private val repository: FlowRepository,
    private val compiler: FlowCompiler,
) {
    fun createDraft(flowKey: String, definition: FlowDefinition): FlowVersion =
        repository.createDraft(requireFlowKey(flowKey), definition)

    fun updateDraft(flowKey: String, version: Int, definition: FlowDefinition): FlowVersion =
        repository.updateDraft(requireFlowKey(flowKey), version, definition)

    fun list(flowKey: String): List<FlowVersion> = repository.list(flowKey)

    fun get(flowKey: String, version: Int): FlowVersion = repository.get(flowKey, version)

    fun validate(definition: FlowDefinition): List<FlowValidationIssue> = compiler.validate(definition)

    @Transactional
    fun publish(flowKey: String, version: Int): FlowVersion {
        return repository.activate(requireFlowKey(flowKey), version) { compiler.compile(it.definition) }
    }

    private fun requireFlowKey(flowKey: String): String {
        require(flowKey.matches(Regex("[A-Za-z0-9._-]{1,120}"))) {
            "flowKey must contain 1-120 letters, digits, dots, underscores or hyphens"
        }
        return flowKey
    }
}

data class ActiveFlow(
    val version: Int,
    val initialState: String,
    val transitionTable: ExTransitionTable<String, String>,
)

@Service
class ActiveFlowProvider(
    private val repository: FlowRepository,
    private val compiler: FlowCompiler,
) {
    private val tables = ConcurrentHashMap<Pair<String, Int>, ExTransitionTable<String, String>>()

    fun get(flowKey: String): ActiveFlow {
        val active = repository.active(flowKey)
        val table = tables.computeIfAbsent(flowKey to active.version) { compiler.compile(active.definition) }
        return ActiveFlow(active.version, active.definition.initialState, table)
    }
}
