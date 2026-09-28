package io.github.ngirchev.fsm.spring.definition

import io.github.ngirchev.fsm.serialization.FsmDto
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

@JvmRecord
data class FlowDefinition @JvmOverloads constructor(
    /** State assigned when a domain object is created from this definition. */
    val initialState: String,
    /** Core FSM structure; handler references are Spring bean names. */
    val table: FsmDto,
    /** Optional visual layout, persisted with the version and ignored by execution. */
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val editor: JsonNode? = null,
    /** Optional application execution bindings. */
    val execution: FlowExecution = FlowExecution(),
)

@JvmRecord
@JsonIgnoreProperties(ignoreUnknown = true)
data class FlowExecution @JvmOverloads constructor(
    val stateListeners: List<String> = emptyList(),
    val completionListeners: List<String> = emptyList(),
)
