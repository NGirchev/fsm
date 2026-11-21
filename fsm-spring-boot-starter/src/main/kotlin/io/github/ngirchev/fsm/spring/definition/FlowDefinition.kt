package io.github.ngirchev.fsm.spring.definition

import io.github.ngirchev.fsm.serialization.FsmDto
import tools.jackson.databind.JsonNode
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

@JvmRecord
data class FlowDefinition @JvmOverloads constructor(
    /** State assigned when a domain object is created from this definition. */
    val initialState: String,
    /**
     * Core FSM structure; handler references are Spring bean names. Its `maxImmediateAutoTransitions`
     * is `0` when the limit is not stated: the core then runs automatic chains without a limit, and a
     * host with its own default replaces the value before loading.
     */
    val table: FsmDto,
    /** Optional visual layout, persisted with the version and ignored by execution. */
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val editor: JsonNode? = null,
    /** Optional application execution bindings. */
    val execution: FlowExecution = FlowExecution(),
    /** States where a run ends, with the outcome the host reports. A final state has no outgoing transitions. */
    @get:JsonInclude(JsonInclude.Include.NON_EMPTY)
    val finalStates: Map<String, FinalStateOutcome> = emptyMap(),
)

/** Result of a run that stopped in a state listed in [FlowDefinition.finalStates]. */
enum class FinalStateOutcome { SUCCESS, FAILURE, LIMIT }

@JvmRecord
@JsonIgnoreProperties(ignoreUnknown = true)
data class FlowExecution @JvmOverloads constructor(
    val stateListeners: List<String> = emptyList(),
    val completionListeners: List<String> = emptyList(),
    /** Host-defined iteration limit of one run; `null` means the host default. The starter only validates it. */
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val maxIterations: Int? = null,
)
