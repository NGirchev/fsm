package io.github.ngirchev.fsm.example.flow

data class FlowDefinition(
    val schemaVersion: Int,
    val initialState: String,
    val autoTransitionEnabled: Boolean = false,
    val allowCyclicAutoTransitions: Boolean = false,
    val maxImmediateAutoTransitions: Int = 0,
    val states: List<String>,
    val events: List<String>,
    val transitions: List<FlowTransitionDefinition>,
)

data class FlowTransitionDefinition(
    val id: String,
    val from: String,
    val to: String,
    val trigger: FlowTriggerDefinition,
    val guards: List<String> = emptyList(),
    val actions: List<String> = emptyList(),
    val postActions: List<String> = emptyList(),
    val timeout: FlowTimeoutDefinition? = null,
)

data class FlowTriggerDefinition(
    val kind: String,
    val event: String? = null,
)

data class FlowTimeoutDefinition(
    val value: Long,
    val unit: String,
)

data class FlowValidationIssue(val path: String, val message: String)

class InvalidFlowDefinitionException(val issues: List<FlowValidationIssue>) :
    IllegalArgumentException("Invalid FSM flow definition")
