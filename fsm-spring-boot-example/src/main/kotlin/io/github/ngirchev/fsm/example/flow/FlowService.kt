package io.github.ngirchev.fsm.example.flow

import io.github.ngirchev.fsm.impl.extended.ExTransitionTable
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.LinkedHashMap

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
    private val tables = object : LinkedHashMap<Pair<String, Int>, ExTransitionTable<String, String>>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<Pair<String, Int>, ExTransitionTable<String, String>>?,
        ): Boolean = size > MAX_CACHED_TABLES
    }

    fun get(flowKey: String): ActiveFlow {
        val active = repository.active(flowKey)
        return active.toActiveFlow()
    }

    fun get(flowKey: String, version: Int): ActiveFlow = repository.get(flowKey, version).toActiveFlow()

    private fun FlowVersion.toActiveFlow(): ActiveFlow {
        val key = flowKey to version
        val table = synchronized(tables) {
            tables.getOrPut(key) { compiler.compile(definition) }
        }
        return ActiveFlow(version, definition.initialState, table)
    }

    companion object {
        private const val MAX_CACHED_TABLES = 128
    }
}

@Service
class ActiveFlowStartupValidator(
    private val repository: FlowRepository,
    private val compiler: FlowCompiler,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        repository.activeVersions().forEach { compiler.compile(it.definition) }
    }
}
